package com.gjp.imp;

import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.dify.DifyClient;
import com.gjp.dify.DifyParseResult;
import com.gjp.entity.Category;
import com.gjp.entity.Record;
import com.gjp.log.OperationLogService;
import com.gjp.mapper.CategoryMapper;
import com.gjp.mapper.ImportFileMapper;
import com.gjp.mapper.ImportItemMapper;
import com.gjp.mapper.ImportJobMapper;
import com.gjp.mapper.MemberMapper;
import com.gjp.record.RecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final long MAX_FILE_BYTES = 12L * 1024 * 1024;
    private static final Set<String> IMAGE_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp", ".bmp");
    private static final Set<String> EXCEL_EXT = Set.of(".xls", ".xlsx", ".csv");
    private static final Set<String> PDF_EXT = Set.of(".pdf");
    private static final List<String> PAY_METHODS = List.of("现金", "微信", "支付宝", "银行卡", "其他");

    private final ImportProperties props;
    private final DifyClient difyClient;
    private final ExecutorService importExecutor;
    private final ImportJobMapper jobMapper;
    private final ImportFileMapper fileMapper;
    private final ImportItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final MemberMapper memberMapper;
    private final RecordService recordService;
    private final OperationLogService logService;
    private final ImportDedup importDedup;

    public ImportService(ImportProperties props, DifyClient difyClient,
                         @Qualifier("importExecutor") ExecutorService importExecutor,
                         ImportJobMapper jobMapper, ImportFileMapper fileMapper, ImportItemMapper itemMapper,
                         CategoryMapper categoryMapper, MemberMapper memberMapper,
                         RecordService recordService, OperationLogService logService,
                         ImportDedup importDedup) {
        this.props = props;
        this.difyClient = difyClient;
        this.importExecutor = importExecutor;
        this.jobMapper = jobMapper;
        this.fileMapper = fileMapper;
        this.itemMapper = itemMapper;
        this.categoryMapper = categoryMapper;
        this.memberMapper = memberMapper;
        this.recordService = recordService;
        this.logService = logService;
        this.importDedup = importDedup;
    }

    public Map<String, Object> config() {
        UserContext.requireFamilyMember();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configured", difyClient.configured());
        map.put("mode", difyClient.mode());
        map.put("maxFiles", props.getMaxFiles());
        map.put("maxFileSizeMb", 12);
        map.put("accept", List.of("jpg", "jpeg", "png", "webp", "bmp", "xls", "xlsx", "csv", "pdf"));
        return map;
    }

    public ImportJob create(MultipartFile[] files, Long memberId) {
        UserContext.requireFamilyMember();
        Long familyId = UserContext.getFamilyId();
        Long userId = UserContext.getUserId();
        Long resolved = UserContext.resolveMemberId(memberId);
        if (resolved == null) {
            resolved = UserContext.get().getMemberId();
        }
        if (resolved == null || memberMapper.selectById(resolved, familyId) == null) {
            throw new BizException("请选择有效的家庭成员");
        }
        if (files == null || files.length == 0) {
            throw new BizException("请至少选择一个文件");
        }
        List<MultipartFile> picked = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) {
                picked.add(f);
            }
        }
        if (picked.isEmpty()) {
            throw new BizException("请至少选择一个文件");
        }
        if (picked.size() > props.getMaxFiles()) {
            throw new BizException("一次最多上传 " + props.getMaxFiles() + " 个文件");
        }

        ImportJob job = new ImportJob();
        job.setFamilyId(familyId);
        job.setUserId(userId);
        job.setMemberId(resolved);
        job.setStatus("queued");
        job.setTotalFiles(picked.size());
        job.setDoneFiles(0);
        job.setExtracted(0);
        job.setImported(0);
        job.setRejected(0);
        jobMapper.insert(job);

        Path dir = Path.of(props.getUploadDir()).toAbsolutePath()
                .resolve(String.valueOf(familyId))
                .resolve(String.valueOf(job.getId()));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException("无法创建上传目录");
        }

        int preDone = 0;
        for (MultipartFile file : picked) {
            ImportFileRow row = saveFile(job, file, dir);
            fileMapper.insert(row);
            if (!"queued".equals(row.getStatus())) {
                preDone++;
                if ("rejected".equals(row.getStatus())) {
                    job.setRejected(nvl(job.getRejected()) + 1);
                }
            }
        }
        job.setDoneFiles(preDone);
        if (preDone >= job.getTotalFiles()) {
            job.setStatus("done");
            job.setMessage("没有可解析的文件（格式需为图片、Excel/CSV 或 PDF）");
            job.setFinishTime(LocalDateTime.now());
            jobMapper.update(job);
            return detail(job.getId());
        }
        jobMapper.update(job);

        Long jobId = job.getId();
        importExecutor.execute(() -> processJob(jobId, familyId));
        return detail(jobId);
    }

    public ImportJob detail(Long id) {
        UserContext.requireFamilyMember();
        Long familyId = UserContext.getFamilyId();
        ImportJob job = jobMapper.selectById(id, familyId);
        if (job == null) {
            throw new BizException("导入任务不存在");
        }
        assertCanView(job);
        List<ImportFileRow> files = fileMapper.selectByJob(id);
        for (ImportFileRow f : files) {
            f.setStoredPath(null);
        }
        job.setFiles(files);
        job.setItems(itemMapper.selectByJob(id));
        importDedup.annotate(job);
        return job;
    }

    public ImportJob confirm(Long id, List<Long> itemIds, boolean merge) {
        UserContext.requireFamilyMember();
        ImportJob job = detail(id);
        if ("queued".equals(job.getStatus()) || "running".equals(job.getStatus())) {
            throw new BizException("智能体还在解析，请等进度走完再确认入库");
        }
        if ("importing".equals(job.getStatus())) {
            throw new BizException("正在入库，请稍候");
        }
        if ("failed".equals(job.getStatus())) {
            throw new BizException("该任务已失败，不能入库");
        }
        List<ImportItem> candidates = new ArrayList<>();
        if (itemIds == null) {
            for (ImportItem item : job.getItems()) {
                if ("pending".equals(item.getStatus())) {
                    candidates.add(item);
                }
            }
        } else {
            if (itemIds.isEmpty()) {
                throw new BizException("请勾选要入库的流水");
            }
            for (Long itemId : itemIds) {
                ImportItem item = itemMapper.selectById(itemId, id);
                if (item == null || !"pending".equals(item.getStatus())) {
                    throw new BizException("勾选的流水不存在或已经处理过");
                }
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) {
            throw new BizException("没有待确认的流水");
        }
        int skip = 0;
        if (merge) {
            List<ImportItem> unique = importDedup.takeUniques(job, candidates);
            for (ImportItem item : candidates) {
                if ("skipped".equals(item.getStatus()) && item.getRejectReason() != null
                        && item.getRejectReason().startsWith("合并：")) {
                    itemMapper.updateStatus(item);
                    skip++;
                }
            }
            candidates = unique;
            if (candidates.isEmpty()) {
                job.setStatus("preview");
                job.setMessage("勾选的流水都与已有记录重复，合并后没有新单可入");
                jobMapper.update(job);
                return detail(id);
            }
        }

        job.setStatus("importing");
        jobMapper.update(job);

        int ok = 0;
        for (ImportItem item : candidates) {
            try {
                Record record = toRecord(job, item);
                recordService.addImported(record);
                item.setStatus("accepted");
                item.setRejectReason(null);
                ok++;
            } catch (BizException e) {
                item.setStatus("skipped");
                item.setRejectReason(e.getMessage());
                skip++;
            }
            itemMapper.updateStatus(item);
        }

        job.setImported(nvl(job.getImported()) + ok);
        boolean stillPending = itemMapper.selectByJob(id).stream().anyMatch(i -> "pending".equals(i.getStatus()));
        job.setStatus(stillPending ? "preview" : "done");
        job.setMessage("已入库 " + ok + " 笔" + (skip > 0 ? "，跳过 " + skip + " 笔" : ""));
        if (!stillPending) {
            job.setFinishTime(LocalDateTime.now());
        }
        jobMapper.update(job);

        logService.record(OperationLogService.M_IMPORT, OperationLogService.A_IMPORT, job.getId(),
                "文件导入入库 " + ok + " 笔流水（成员 " + (job.getMemberName() == null ? job.getMemberId() : job.getMemberName()) + "）");
        return detail(id);
    }

    private void processJob(Long jobId, Long familyId) {
        ImportJob job = jobMapper.selectById(jobId, familyId);
        if (job == null) {
            return;
        }
        try {
            job.setStatus("running");
            jobMapper.update(job);
            CategoryBinder binder = CategoryBinder.from(categoryMapper.selectByFamily(familyId, null));
            String categories = binder.listText();
            for (ImportFileRow file : fileMapper.selectByJob(jobId)) {
                if (!"queued".equals(file.getStatus())) {
                    continue;
                }
                processFile(job, file, binder, categories);
                job.setDoneFiles(nvl(job.getDoneFiles()) + 1);
                jobMapper.update(job);
            }
            finishParse(job);
        } catch (Exception e) {
            log.warn("导入任务 {} 失败：{}", jobId, e.getMessage());
            job.setStatus("failed");
            job.setMessage("任务失败：" + cut(e.getMessage(), 200));
            job.setFinishTime(LocalDateTime.now());
            jobMapper.update(job);
        }
    }

    private void processFile(ImportJob job, ImportFileRow file, CategoryBinder binder, String categories) {
        file.setStatus("parsing");
        file.setProgress(8);
        fileMapper.update(file);
        try {
            byte[] bytes = Files.readAllBytes(Path.of(file.getStoredPath()));
            if ("excel".equals(file.getKind())) {
                parseExcel(job, file, bytes, binder, categories);
            } else {
                parseAttachment(job, file, bytes, binder, categories);
            }
        } catch (BizException e) {
            failFile(job, file, e.getMessage());
        } catch (Exception e) {
            log.warn("解析文件 {} 失败：{}", file.getOriginalName(), e.getMessage());
            failFile(job, file, cut(e.getMessage(), 200));
        }
    }

    private void parseExcel(ImportJob job, ImportFileRow file, byte[] bytes,
                            CategoryBinder binder, String categories) {
        String table;
        List<String> chunks;
        try {
            table = ExcelTextExtractor.tableText(bytes, file.getOriginalName());
            chunks = ExcelTextExtractor.chunks(bytes, file.getOriginalName());
        } catch (IllegalArgumentException e) {
            failFile(job, file, e.getMessage());
            return;
        }
        if ((table == null || table.isBlank()) && chunks.isEmpty()) {
            rejectFile(job, file, "表格是空的，不像账单");
            return;
        }
        DifyParseResult local = BillTextParser.parse(table);
        if (local.isRelevant() && local.getRecords() != null && !local.getRecords().isEmpty()) {
            saveRecords(job, file, local, binder);
            file.setStatus("ready");
            file.setProgress(100);
            fileMapper.update(file);
            return;
        }
        if (chunks.isEmpty()) {
            rejectFile(job, file, local.getReason() == null ? "表格是空的，不像账单" : local.getReason());
            return;
        }
        boolean anyRelevant = false;
        String lastReason = local.getReason();
        for (int i = 0; i < chunks.size(); i++) {
            try {
                DifyParseResult parsed = parseChunk(file, chunks.get(i), categories);
                if (parsed.isRelevant()) {
                    anyRelevant = true;
                    saveRecords(job, file, parsed, binder);
                } else {
                    lastReason = parsed.getReason();
                }
            } catch (BizException e) {
                lastReason = e.getMessage();
                log.warn("表格分块 {}/{} 调用智能体失败：{}", i + 1, chunks.size(), e.getMessage());
            }
            file.setProgress((i + 1) * 100 / chunks.size());
            fileMapper.update(file);
        }
        if (!anyRelevant) {
            rejectFile(job, file, lastReason == null ? "智能体认为这不是账单" : lastReason);
            return;
        }
        file.setStatus("ready");
        file.setProgress(100);
        fileMapper.update(file);
    }

    private DifyParseResult parseChunk(ImportFileRow file, String chunk, String categories) {
        if (difyClient.configured()) {
            return difyClient.parse(null, file.getOriginalName(), file.getContentType(),
                    "excel", chunk, categories);
        }
        return BillTextParser.parse(chunk);
    }

    private void parseAttachment(ImportJob job, ImportFileRow file, byte[] bytes,
                                 CategoryBinder binder, String categories) {
        if ("pdf".equals(file.getKind())) {
            parsePdf(job, file, bytes, binder, categories);
            return;
        }
        if (!difyClient.configured()) {
            failFile(job, file, "请先配置 Dify API Key，图片需要智能体识别");
            return;
        }
        file.setProgress(20);
        fileMapper.update(file);
        DifyParseResult parsed = difyClient.parse(bytes, file.getOriginalName(), file.getContentType(),
                file.getKind(), null, categories);
        acceptParsed(job, file, parsed, binder);
    }

    private void parsePdf(ImportJob job, ImportFileRow file, byte[] bytes,
                          CategoryBinder binder, String categories) {
        PdfSupport.Prepared prepared;
        try {
            prepared = PdfSupport.prepare(bytes);
        } catch (IllegalArgumentException e) {
            failFile(job, file, e.getMessage());
            return;
        }
        file.setProgress(20);
        fileMapper.update(file);
        DifyParseResult parsed;
        if (prepared.hasText() && WeChatPdfParser.looksLike(prepared.text())) {
            parsed = WeChatPdfParser.parse(prepared.text());
        } else if (prepared.hasText()) {
            DifyParseResult local = BillTextParser.parse(prepared.text());
            parsed = local.isRelevant() ? local : parseChunk(file, prepared.text(), categories);
        } else if (prepared.hasImage()) {
            if (!difyClient.configured()) {
                failFile(job, file, "请先配置 Dify API Key，扫描件 PDF 需要智能体识别");
                return;
            }
            parsed = difyClient.parse(prepared.imagePng(), file.getOriginalName() + ".png",
                    "image/png", "image", null, categories);
        } else {
            failFile(job, file, "这份 PDF 没有可提取的文字，也无法渲染成图片");
            return;
        }
        acceptParsed(job, file, parsed, binder);
    }

    private void acceptParsed(ImportJob job, ImportFileRow file, DifyParseResult parsed, CategoryBinder binder) {
        if (!parsed.isRelevant()) {
            rejectFile(job, file, parsed.getReason() == null ? "智能体认为这不是账单" : parsed.getReason());
            return;
        }
        saveRecords(job, file, parsed, binder);
        file.setStatus("ready");
        file.setProgress(100);
        fileMapper.update(file);
    }

    private void saveRecords(ImportJob job, ImportFileRow file, DifyParseResult parsed, CategoryBinder binder) {
        for (Map<String, Object> raw : parsed.getRecords()) {
            ImportItem item = fromAgent(job, file, raw, binder);
            itemMapper.insert(item);
            file.setExtracted(nvl(file.getExtracted()) + 1);
            job.setExtracted(nvl(job.getExtracted()) + 1);
        }
    }

    private ImportItem fromAgent(ImportJob job, ImportFileRow file, Map<String, Object> raw, CategoryBinder binder) {
        ImportItem item = new ImportItem();
        item.setJobId(job.getId());
        item.setFileId(file.getId());
        item.setFamilyId(job.getFamilyId());
        item.setStatus("pending");
        Integer type = toType(raw.get("type"));
        item.setType(type);
        item.setAmount(toAmount(raw.get("amount")));
        item.setRecordDate(toDate(raw.get("recordDate")));
        String categoryName = str(raw.get("categoryName"));
        String merchant = trimTo(str(raw.get("merchant")), 100);
        String remark = trimTo(str(raw.get("remark")), 255);
        if (categoryName == null || categoryName.isBlank()) {
            categoryName = BillCategoryHints.guess(type, merchant, remark);
        }
        item.setCategoryName(categoryName);
        item.setMerchant(merchant);
        item.setRemark(remark);
        Category cat = binder.match(type, item.getCategoryName());
        if (cat != null) {
            item.setCategoryId(cat.getId());
            item.setCategoryName(cat.getCategoryName());
        }
        item.setArea(trimTo(str(raw.get("area")), 50));
        item.setPayMethod(normalizePay(str(raw.get("payMethod"))));
        item.setIsGift(toGift(raw.get("isGift")));

        if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            item.setStatus("skipped");
            item.setRejectReason("金额无效");
        } else if (item.getRecordDate() == null) {
            item.setStatus("skipped");
            item.setRejectReason("日期无效");
        } else if (item.getRecordDate().isAfter(LocalDate.now())) {
            item.setStatus("skipped");
            item.setRejectReason("发生日期不能晚于今天");
        } else if (item.getCategoryId() == null) {
            item.setStatus("skipped");
            item.setRejectReason("无法匹配分类");
        }
        return item;
    }

    private Record toRecord(ImportJob job, ImportItem item) {
        Record record = new Record();
        record.setMemberId(job.getMemberId());
        record.setType(item.getType());
        record.setCategoryId(item.getCategoryId());
        record.setAmount(item.getAmount());
        record.setRecordDate(item.getRecordDate());
        record.setMerchant(item.getMerchant());
        record.setArea(item.getArea());
        record.setPayMethod(item.getPayMethod());
        record.setIsGift(item.getIsGift() == null ? 0 : item.getIsGift());
        record.setRemark(item.getRemark());
        return record;
    }

    private void rejectFile(ImportJob job, ImportFileRow file, String reason) {
        file.setStatus("rejected");
        file.setProgress(100);
        file.setRejectReason(cut(reason, 400));
        fileMapper.update(file);
        job.setRejected(nvl(job.getRejected()) + 1);
    }

    private void failFile(ImportJob job, ImportFileRow file, String reason) {
        file.setStatus("failed");
        file.setProgress(100);
        file.setRejectReason(cut(reason, 400));
        fileMapper.update(file);
    }

    private void finishParse(ImportJob job) {
        if (nvl(job.getExtracted()) > 0) {
            job.setStatus("preview");
            job.setMessage("抽出 " + job.getExtracted() + " 笔，请勾选后确认入库");
        } else {
            job.setStatus("done");
            job.setFinishTime(LocalDateTime.now());
            String reason = fileMapper.selectByJob(job.getId()).stream()
                    .map(ImportFileRow::getRejectReason)
                    .filter(s -> s != null && !s.isBlank())
                    .findFirst()
                    .orElse("没有抽出流水");
            job.setMessage(reason);
        }
        jobMapper.update(job);
    }

    private ImportFileRow saveFile(ImportJob job, MultipartFile file, Path dir) {
        String original = safeName(file.getOriginalFilename());
        String ext = extension(original);
        String kind = kindOf(original, file.getContentType());
        ImportFileRow row = new ImportFileRow();
        row.setJobId(job.getId());
        row.setFamilyId(job.getFamilyId());
        row.setOriginalName(original);
        row.setContentType(file.getContentType());
        row.setFileSize(file.getSize());
        row.setKind(kind);
        row.setProgress(0);
        row.setExtracted(0);
        if (file.getSize() <= 0) {
            row.setStatus("rejected");
            row.setRejectReason("空文件");
            row.setStoredPath("");
            return row;
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            row.setStatus("rejected");
            row.setRejectReason("单个文件不能超过 12MB");
            row.setStoredPath("");
            return row;
        }
        if ("other".equals(kind)) {
            row.setStatus("rejected");
            row.setRejectReason("仅支持图片（jpg/png/webp/bmp）、Excel/CSV 或 PDF");
            row.setStoredPath("");
            return row;
        }
        Path dest = dir.resolve(UUID.randomUUID() + (ext.isEmpty() ? "" : ext));
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            row.setStatus("failed");
            row.setRejectReason("保存文件失败");
            row.setStoredPath("");
            return row;
        }
        row.setStoredPath(dest.toAbsolutePath().toString());
        row.setStatus("queued");
        return row;
    }

    private void assertCanView(ImportJob job) {
        Long scope = UserContext.scopeMemberId();
        if (scope != null && !UserContext.getUserId().equals(job.getUserId())) {
            throw new BizException("导入任务不存在");
        }
    }

    private static String kindOf(String name, String mime) {
        String ext = extension(name);
        if (IMAGE_EXT.contains(ext) || (mime != null && mime.startsWith("image/"))) {
            return "image";
        }
        if (EXCEL_EXT.contains(ext) || (mime != null && (mime.contains("excel") || mime.contains("spreadsheet")
                || "text/csv".equals(mime)))) {
            return "excel";
        }
        if (PDF_EXT.contains(ext) || (mime != null && mime.contains("pdf"))) {
            return "pdf";
        }
        return "other";
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "未命名文件";
        }
        String plain = Path.of(name).getFileName().toString().replace("\0", "");
        return plain.isBlank() ? "未命名文件" : cut(plain, 180);
    }

    private static Integer toType(Object v) {
        if (v instanceof Number n) {
            int i = n.intValue();
            return i == 1 ? 1 : 2;
        }
        Integer parsed = BillTextParser.parseType(str(v));
        return parsed == null ? 2 : parsed;
    }

    private static BigDecimal toAmount(Object v) {
        if (v instanceof Number n) {
            return new BigDecimal(n.toString()).abs();
        }
        return BillTextParser.parseAmount(str(v));
    }

    private static LocalDate toDate(Object v) {
        if (v instanceof LocalDate d) {
            return d;
        }
        return BillTextParser.parseDate(str(v));
    }

    private static int toGift(Object v) {
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Number n) {
            return n.intValue() == 1 ? 1 : 0;
        }
        return BillTextParser.parseGift(str(v));
    }

    private static String normalizePay(String raw) {
        String pay = BillTextParser.normalizePay(raw);
        if (pay.isEmpty()) {
            return null;
        }
        return PAY_METHODS.contains(pay) ? pay : null;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}

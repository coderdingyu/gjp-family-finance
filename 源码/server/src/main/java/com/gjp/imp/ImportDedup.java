package com.gjp.imp;

import com.gjp.dedup.OrderNos;
import com.gjp.mapper.RecordMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 导入入库前的查重。
 * <p>
 * 订单号（两边都非空且忽略大小写相同）视为强重复，即使日期/金额/商家不同也只保留一份。
 * 只有一边有订单号时不走订单号，仍按指纹：同一天、同收支、同金额、同商家视为一份。
 * 合并时按「每个来源文件的次数」与账本已有次数取差，避免 Excel+PDF 双份，也不误伤同一天两笔一样的订水。
 */
@Component
public class ImportDedup {

    private static final String HINT_LEDGER = "与账本已有流水重复";
    private static final String HINT_BATCH = "与本次其他文件重复";
    private static final String SKIP_LEDGER = "合并：与账本已有流水重复，只保留一份";
    private static final String SKIP_BATCH = "合并：与本次其他文件重复，只保留一份";
    private static final String SKIP_LEDGER_ORDER = "合并：与账本已有流水重复（订单号相同）";
    private static final String SKIP_BATCH_ORDER = "合并：与本次其他文件重复（订单号相同）";

    private final RecordMapper recordMapper;

    public ImportDedup(RecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    public void annotate(ImportJob job) {
        if (job.getItems() == null || job.getItems().isEmpty() || job.getMemberId() == null) {
            job.setDuplicateCount(0);
            return;
        }
        Counters c = buildCounters(job, job.getItems());
        int dups = 0;
        Map<String, Integer> seenOrder = new HashMap<>();
        Map<String, Integer> seenFp = new HashMap<>();
        for (ImportItem item : job.getItems()) {
            if (!"pending".equals(item.getStatus())) {
                continue;
            }
            Hit hit = classify(item, c, seenOrder, seenFp);
            if (hit != null) {
                item.setDuplicateKind(hit.kind);
                item.setDuplicateHint(hit.hint);
                dups++;
            }
        }
        job.setDuplicateCount(dups);
    }

    public List<ImportItem> takeUniques(ImportJob job, List<ImportItem> candidates) {
        Counters c = buildCounters(job, candidates);
        Map<String, Integer> seenOrder = new HashMap<>();
        Map<String, Integer> seenFp = new HashMap<>();
        List<ImportItem> keep = new ArrayList<>();
        for (ImportItem item : candidates) {
            Hit hit = classify(item, c, seenOrder, seenFp);
            if (hit == null) {
                keep.add(item);
            } else {
                item.setStatus("skipped");
                item.setRejectReason(hit.skipReason);
            }
        }
        return keep;
    }

    /**
     * 有订单号则只走订单号计数；否则走指纹。订单号重复即使指纹还能留也要跳过。
     */
    private static Hit classify(ImportItem item, Counters c,
                                Map<String, Integer> seenOrder, Map<String, Integer> seenFp) {
        String orderKey = orderKey(item);
        if (orderKey != null) {
            int ledger = c.ledgerOrder.getOrDefault(orderKey, 0);
            int allow = allow(c.fileOrder.getOrDefault(orderKey, Map.of()), ledger);
            int used = seenOrder.getOrDefault(orderKey, 0);
            seenOrder.put(orderKey, used + 1);
            if (used >= allow) {
                return Hit.of(ledger > 0, true);
            }
            return null;
        }
        String key = fingerprint(item);
        int ledger = c.ledgerFp.getOrDefault(key, 0);
        int allow = allow(c.fileFp.getOrDefault(key, Map.of()), ledger);
        int used = seenFp.getOrDefault(key, 0);
        seenFp.put(key, used + 1);
        if (used >= allow) {
            return Hit.of(ledger > 0, false);
        }
        return null;
    }

    static String fingerprint(ImportItem item) {
        String date = item.getRecordDate() == null ? "" : item.getRecordDate().toString();
        String type = item.getType() == null ? "" : String.valueOf(item.getType());
        String amount = item.getAmount() == null ? "" : item.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String merchant = compact(item.getMerchant());
        return date + "|" + type + "|" + amount + "|" + merchant;
    }

    static String orderKey(ImportItem item) {
        String n = OrderNos.normalize(item == null ? null : item.getOrderNo());
        return n == null ? null : n.toLowerCase(Locale.ROOT);
    }

    private Counters buildCounters(ImportJob job, List<ImportItem> items) {
        Counters c = new Counters();
        c.ledgerFp = ledgerCounts(job);
        c.ledgerOrder = ledgerOrderNos(job);
        c.fileFp = fileCounts(items, false);
        c.fileOrder = fileCounts(items, true);
        return c;
    }

    private Map<String, Integer> ledgerCounts(ImportJob job) {
        Map<String, Integer> map = new HashMap<>();
        List<Map<String, Object>> rows = recordMapper.countImportFingerprints(job.getFamilyId(), job.getMemberId());
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String date = String.valueOf(row.get("recordDate"));
            String type = String.valueOf(row.get("type"));
            BigDecimal amount = toAmount(row.get("amount"));
            String merchant = compact(String.valueOf(row.getOrDefault("merchant", "")));
            String key = date + "|" + type + "|"
                    + (amount == null ? "" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    + "|" + merchant;
            map.put(key, ((Number) row.get("cnt")).intValue());
        }
        return map;
    }

    private Map<String, Integer> ledgerOrderNos(ImportJob job) {
        Map<String, Integer> map = new HashMap<>();
        List<Map<String, Object>> rows = recordMapper.countImportOrderNos(job.getFamilyId(), job.getMemberId());
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object raw = row.get("orderNo");
            if (raw == null) {
                raw = row.get("orderno");
            }
            String key = raw == null ? null : OrderNos.normalize(String.valueOf(raw));
            if (key == null) {
                continue;
            }
            key = key.toLowerCase(Locale.ROOT);
            map.put(key, ((Number) row.get("cnt")).intValue());
        }
        return map;
    }

    private static Map<String, Map<Long, Integer>> fileCounts(List<ImportItem> items, boolean byOrderNo) {
        Map<String, Map<Long, Integer>> map = new LinkedHashMap<>();
        if (items == null) {
            return map;
        }
        for (ImportItem item : items) {
            if (!"pending".equals(item.getStatus()) && item.getStatus() != null
                    && !"skipped".equals(item.getStatus())) {
                // accepted already in ledger; pending/skipped still count for batch
            }
            if (item.getStatus() != null && !"pending".equals(item.getStatus())) {
                continue;
            }
            String key = byOrderNo ? orderKey(item) : fingerprint(item);
            if (key == null) {
                continue;
            }
            Long fileId = item.getFileId() == null ? 0L : item.getFileId();
            map.computeIfAbsent(key, k -> new HashMap<>()).merge(fileId, 1, Integer::sum);
        }
        return map;
    }

    private static int allow(Map<Long, Integer> perFile, int ledger) {
        int maxFile = 0;
        for (int n : perFile.values()) {
            if (n > maxFile) {
                maxFile = n;
            }
        }
        return Math.max(0, maxFile - ledger);
    }

    private static BigDecimal toAmount(Object v) {
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return v == null ? null : new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String compact(String s) {
        if (s == null || "null".equals(s)) {
            return "";
        }
        return s.replaceAll("\\s+", "");
    }

    private static final class Counters {
        Map<String, Integer> ledgerFp = Map.of();
        Map<String, Integer> ledgerOrder = Map.of();
        Map<String, Map<Long, Integer>> fileFp = Map.of();
        Map<String, Map<Long, Integer>> fileOrder = Map.of();
    }

    private static final class Hit {
        final String kind;
        final String hint;
        final String skipReason;

        private Hit(String kind, String hint, String skipReason) {
            this.kind = kind;
            this.hint = hint;
            this.skipReason = skipReason;
        }

        static Hit of(boolean ledger, boolean orderNo) {
            if (ledger) {
                return new Hit("ledger", HINT_LEDGER, orderNo ? SKIP_LEDGER_ORDER : SKIP_LEDGER);
            }
            return new Hit("batch", HINT_BATCH, orderNo ? SKIP_BATCH_ORDER : SKIP_BATCH);
        }
    }
}

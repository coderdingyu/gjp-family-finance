package com.gjp.imp;

import com.gjp.mapper.RecordMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入入库前的查重：同一天、同收支、同金额、同商家视为一份。
 * 合并时按「每个来源文件的次数」与账本已有次数取差，避免 Excel+PDF 双份，也不误伤同一天两笔一样的订水。
 */
@Component
public class ImportDedup {

    private final RecordMapper recordMapper;

    public ImportDedup(RecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    public void annotate(ImportJob job) {
        if (job.getItems() == null || job.getItems().isEmpty() || job.getMemberId() == null) {
            job.setDuplicateCount(0);
            return;
        }
        Map<String, Integer> ledger = ledgerCounts(job);
        Map<String, Map<Long, Integer>> fileCounts = fileCounts(job.getItems());
        int dups = 0;
        Map<String, Integer> seen = new HashMap<>();
        for (ImportItem item : job.getItems()) {
            if (!"pending".equals(item.getStatus())) {
                continue;
            }
            String key = fingerprint(item);
            int allow = allow(key, fileCounts.getOrDefault(key, Map.of()), ledger.getOrDefault(key, 0));
            int used = seen.getOrDefault(key, 0);
            if (used >= allow) {
                item.setDuplicateKind(ledger.getOrDefault(key, 0) > 0 ? "ledger" : "batch");
                item.setDuplicateHint(ledger.getOrDefault(key, 0) > 0 ? "与账本已有流水重复" : "与本次其他文件重复");
                dups++;
            }
            seen.put(key, used + 1);
        }
        job.setDuplicateCount(dups);
    }

    public List<ImportItem> takeUniques(ImportJob job, List<ImportItem> candidates) {
        Map<String, Integer> ledger = ledgerCounts(job);
        Map<String, Map<Long, Integer>> fileCounts = fileCounts(candidates);
        Map<String, Integer> kept = new HashMap<>();
        List<ImportItem> keep = new ArrayList<>();
        for (ImportItem item : candidates) {
            String key = fingerprint(item);
            int allow = allow(key, fileCounts.getOrDefault(key, Map.of()), ledger.getOrDefault(key, 0));
            int used = kept.getOrDefault(key, 0);
            if (used < allow) {
                keep.add(item);
                kept.put(key, used + 1);
            } else {
                item.setStatus("skipped");
                item.setRejectReason(ledger.getOrDefault(key, 0) > 0
                        ? "合并：与账本已有流水重复，只保留一份"
                        : "合并：与本次其他文件重复，只保留一份");
            }
        }
        return keep;
    }

    static String fingerprint(ImportItem item) {
        String date = item.getRecordDate() == null ? "" : item.getRecordDate().toString();
        String type = item.getType() == null ? "" : String.valueOf(item.getType());
        String amount = item.getAmount() == null ? "" : item.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String merchant = compact(item.getMerchant());
        return date + "|" + type + "|" + amount + "|" + merchant;
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

    private static Map<String, Map<Long, Integer>> fileCounts(List<ImportItem> items) {
        Map<String, Map<Long, Integer>> map = new LinkedHashMap<>();
        for (ImportItem item : items) {
            if (!"pending".equals(item.getStatus()) && item.getStatus() != null
                    && !"skipped".equals(item.getStatus())) {
                // accepted already in ledger; pending/skipped still count for batch
            }
            if (item.getStatus() != null && !"pending".equals(item.getStatus())) {
                continue;
            }
            String key = fingerprint(item);
            Long fileId = item.getFileId() == null ? 0L : item.getFileId();
            map.computeIfAbsent(key, k -> new HashMap<>()).merge(fileId, 1, Integer::sum);
        }
        return map;
    }

    private static int allow(String key, Map<Long, Integer> perFile, int ledger) {
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
}

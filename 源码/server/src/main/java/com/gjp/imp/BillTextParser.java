package com.gjp.imp;

import com.gjp.dify.DifyParseResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 未配置 Dify 时，用表头规则从 Excel/CSV 文本里抽流水，方便本地把队列和确认入库跑通。
 * 图片 / PDF 不会走到这里。
 */
public final class BillTextParser {

    private static final DateTimeFormatter[] DATES = {
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    };
    private static final Pattern MD = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern MONEY = Pattern.compile("^[+\\-]?\\d+(\\.\\d+)?$");

    private BillTextParser() {
    }

    public static DifyParseResult parse(String tsv) {
        DifyParseResult result = new DifyParseResult();
        if (tsv == null || tsv.isBlank()) {
            result.setRelevant(false);
            result.setReason("表格是空的，不像账单");
            return result;
        }
        String[] lines = tsv.split("\\r?\\n");
        int headerAt = findHeader(lines);
        if (headerAt < 0) {
            result.setRelevant(false);
            result.setReason("表格里找不到日期或金额列，不像家庭账单");
            return result;
        }
        String[] headers = split(lines[headerAt]);
        int[] cols = mapColumns(headers);
        if (cols[0] < 0 || (cols[1] < 0 && cols[2] < 0 && cols[3] < 0)) {
            result.setRelevant(false);
            result.setReason("表格里找不到日期或金额列，不像家庭账单");
            return result;
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = headerAt + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            Map<String, Object> rec = parseRow(split(line), cols);
            if (rec != null) {
                records.add(rec);
            }
        }
        if (records.isEmpty()) {
            result.setRelevant(false);
            result.setReason("能认出表头，但没有抽出有效的日期+金额行");
            return result;
        }
        result.setRelevant(true);
        result.setRecords(records);
        return result;
    }

    private static int findHeader(String[] lines) {
        int limit = Math.min(lines.length, 40);
        for (int i = 0; i < limit; i++) {
            int[] cols = mapColumns(split(lines[i]));
            if (cols[0] >= 0 && (cols[1] >= 0 || cols[2] >= 0 || cols[3] >= 0)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 0 date, 1 amount, 2 income, 3 expense, 4 type, 5 category,
     * 6 merchant, 7 area, 8 pay, 9 gift, 10 remark, 11 product
     */
    private static int[] mapColumns(String[] headers) {
        int[] cols = new int[12];
        java.util.Arrays.fill(cols, -1);
        for (int i = 0; i < headers.length; i++) {
            String h = compact(headers[i]).toLowerCase(Locale.ROOT);
            if (h.isEmpty()) {
                continue;
            }
            if (cols[0] < 0 && looksDate(h)) {
                cols[0] = i;
            } else if (cols[2] < 0 && (h.contains("收入金额") || h.equals("收入") || h.contains("inamount"))) {
                cols[2] = i;
            } else if (cols[3] < 0 && (h.contains("支出金额") || h.equals("支出") || h.contains("outamount"))) {
                cols[3] = i;
            } else if (cols[1] < 0 && looksAmount(h)) {
                cols[1] = i;
            } else if (isInOutHeader(h)) {
                cols[4] = i;
            } else if (cols[4] < 0 && isTypeHeader(h)) {
                cols[4] = i;
            } else if (cols[5] < 0 && (h.contains("分类") || h.contains("类别") || h.contains("category"))) {
                cols[5] = i;
            } else if (cols[6] < 0 && looksMerchant(h)) {
                cols[6] = i;
            } else if (cols[7] < 0 && (h.contains("片区") || h.contains("地区") || h.contains("城市") || h.equals("area"))) {
                cols[7] = i;
            } else if (cols[8] < 0 && (h.contains("支付") || h.contains("pay"))) {
                cols[8] = i;
            } else if (cols[9] < 0 && (h.contains("人情") || h.contains("礼金") || h.contains("gift"))) {
                cols[9] = i;
            } else if (cols[11] < 0 && (h.contains("商品") || h.contains("品名"))) {
                cols[11] = i;
            } else if (cols[10] < 0 && (h.contains("备注") || h.contains("说明") || h.contains("remark") || h.contains("note"))) {
                cols[10] = i;
            }
        }
        return cols;
    }

    private static boolean isInOutHeader(String h) {
        return h.equals("收/支") || h.equals("收／支") || h.equals("收支") || h.contains("收/支");
    }

    private static boolean isTypeHeader(String h) {
        if (h.contains("交易类型") || h.contains("交易方式")) {
            return false;
        }
        return h.equals("类型") || h.equals("type") || h.contains("类型");
    }

    private static Map<String, Object> parseRow(String[] cells, int[] cols) {
        LocalDate date = parseDate(cell(cells, cols[0]));
        if (date == null || date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(2000, 1, 1))) {
            return null;
        }
        if (isNeutral(cell(cells, cols[4]))) {
            return null;
        }
        int type = 2;
        BigDecimal amount = null;
        BigDecimal income = parseAmount(cell(cells, cols[2]));
        BigDecimal expense = parseAmount(cell(cells, cols[3]));
        if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
            type = 1;
            amount = income;
        } else if (expense != null && expense.compareTo(BigDecimal.ZERO) > 0) {
            type = 2;
            amount = expense;
        } else {
            amount = parseAmount(cell(cells, cols[1]));
            Integer typed = parseType(cell(cells, cols[4]));
            if (typed != null) {
                type = typed;
            }
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", type);
        rec.put("amount", amount);
        rec.put("recordDate", date.toString());
        String remark = firstMeaningful(cell(cells, cols[10]), cell(cells, cols[11]));
        rec.put("categoryName", cell(cells, cols[5]));
        rec.put("merchant", cell(cells, cols[6]));
        rec.put("area", cell(cells, cols[7]));
        rec.put("payMethod", normalizePay(cell(cells, cols[8])));
        rec.put("isGift", parseGift(cell(cells, cols[9])));
        rec.put("remark", remark);
        return rec;
    }

    private static boolean isNeutral(String raw) {
        if (raw == null) {
            return false;
        }
        String s = compact(raw);
        return s.equals("/") || s.equals("／") || s.contains("中性");
    }

    private static String firstMeaningful(String... values) {
        for (String v : values) {
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty() && !"/".equals(t) && !"／".equals(t)) {
                    return t;
                }
            }
        }
        return "";
    }

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() >= 10 && (s.charAt(4) == '-' || s.charAt(4) == '/' || s.charAt(4) == '.')) {
            String head = s.substring(0, 10).replace('/', '-').replace('.', '-');
            try {
                return LocalDate.parse(head);
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        for (DateTimeFormatter fmt : DATES) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        Matcher m = MD.matcher(s);
        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            try {
                return LocalDate.of(LocalDate.now().getYear(), month, day);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().replace("¥", "").replace("￥", "")
                .replace(",", "").replace("，", "").replace(" ", "");
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1);
        }
        if (!MONEY.matcher(s).matches()) {
            return null;
        }
        try {
            return new BigDecimal(s).abs();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = compact(raw).toLowerCase(Locale.ROOT);
        if (s.contains("收") || s.equals("1") || s.contains("income")) {
            return 1;
        }
        if (s.contains("支") || s.equals("2") || s.contains("expense")) {
            return 2;
        }
        return null;
    }

    static int parseGift(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String s = compact(raw).toLowerCase(Locale.ROOT);
        if (s.equals("1") || s.equals("是") || s.equals("true") || s.contains("礼") || s.contains("红包")) {
            return 1;
        }
        return 0;
    }

    static String normalizePay(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = compact(raw);
        if (s.contains("微信") || s.contains("零钱") || s.toLowerCase(Locale.ROOT).contains("wechat")) {
            return "微信";
        }
        if (s.contains("支付宝") || s.toLowerCase(Locale.ROOT).contains("alipay")) {
            return "支付宝";
        }
        if (s.contains("现金") || s.toLowerCase(Locale.ROOT).contains("cash")) {
            return "现金";
        }
        if (s.contains("银行卡") || s.contains("信用卡") || s.contains("储蓄卡") || s.contains("卡")) {
            return "银行卡";
        }
        if (s.contains("其他")) {
            return "其他";
        }
        return "";
    }

    private static boolean looksDate(String h) {
        return h.contains("日期") || h.contains("时间") || h.equals("date") || h.contains("datetime")
                || h.contains("交易日") || h.contains("消费日") || h.contains("发生日");
    }

    private static boolean looksAmount(String h) {
        return h.contains("金额") || h.equals("amount") || h.equals("money") || h.contains("价");
    }

    private static boolean looksMerchant(String h) {
        return h.contains("商家") || h.contains("商户") || h.contains("店铺") || h.contains("对方")
                || h.contains("merchant") || h.contains("shop");
    }

    private static String[] split(String line) {
        if (line == null) {
            return new String[0];
        }
        if (line.contains("\t")) {
            return line.split("\t", -1);
        }
        return line.split(",", -1);
    }

    private static String cell(String[] cells, int idx) {
        if (idx < 0 || idx >= cells.length) {
            return "";
        }
        return cells[idx] == null ? "" : cells[idx].trim();
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}

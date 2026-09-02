package com.gjp.imp;

import com.gjp.dify.DifyParseResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析微信「交易明细证明」PDF 抽出来的折行文本。
 * 把日期、时间后面的字段拼成一行，再切出收支、支付方式、金额和商家。
 */
public final class WeChatPdfParser {

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern TIME = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
    private static final Pattern JOINED = Pattern.compile(
            "^(.*?)\\s+(收入|支出|其他)(?:\\s+(\\S+)\\s+(\\d+(?:\\.\\d{1,2})?)\\s*(.*))?$");

    private WeChatPdfParser() {
    }

    public static boolean looksLike(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("微信支付") && (text.contains("交易明细证明") || text.contains("收/支"));
    }

    public static DifyParseResult parse(String text) {
        DifyParseResult result = new DifyParseResult();
        List<String> lines = lines(text);
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.size() - 1; i++) {
            if (DATE.matcher(lines.get(i)).matches() && TIME.matcher(lines.get(i + 1)).matches()) {
                starts.add(i);
            }
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (int n = 0; n < starts.size(); n++) {
            int from = starts.get(n);
            int to = n + 1 < starts.size() ? starts.get(n + 1) : lines.size();
            Map<String, Object> rec = parseBlock(lines.subList(from, to));
            if (rec != null) {
                records.add(rec);
            }
        }
        if (records.isEmpty()) {
            result.setRelevant(false);
            result.setReason("这是微信账单证明，但没有抽出有效流水");
            return result;
        }
        result.setRelevant(true);
        result.setRecords(records);
        return result;
    }

    private static Map<String, Object> parseBlock(List<String> block) {
        if (block.size() < 3) {
            return null;
        }
        LocalDate date = BillTextParser.parseDate(block.get(0));
        if (date == null) {
            return null;
        }
        String joined = String.join(" ", block.subList(2, block.size())).replaceAll("\\s+", " ").trim();
        Matcher m = JOINED.matcher(joined);
        if (!m.matches()) {
            return null;
        }
        String io = m.group(2);
        if ("其他".equals(io) || "/".equals(io) || "／".equals(io)) {
            return null;
        }
        BigDecimal amount = BillTextParser.parseAmount(m.group(4));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        Integer type = BillTextParser.parseType(io);
        if (type == null) {
            return null;
        }
        String kind = m.group(1) == null ? "" : m.group(1).trim();
        String pay = m.group(3) == null ? "" : m.group(3);
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", type);
        rec.put("amount", amount);
        rec.put("recordDate", date.toString());
        rec.put("categoryName", "");
        rec.put("merchant", takeMerchant(m.group(5)));
        rec.put("area", "");
        rec.put("payMethod", BillTextParser.normalizePay(pay));
        rec.put("isGift", kind.contains("红包") ? 1 : 0);
        rec.put("remark", kind);
        return rec;
    }

    static String takeMerchant(String rest) {
        if (rest == null || rest.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String tok : rest.trim().split("\\s+")) {
            if (isOrderLine(tok)) {
                break;
            }
            sb.append(tok);
        }
        return stripOrderTail(sb.toString());
    }

    static boolean isOrderLine(String s) {
        if (s == null || s.isBlank()) {
            return true;
        }
        if ("/".equals(s) || "／".equals(s)) {
            return true;
        }
        int digits = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                digits++;
            }
        }
        if (digits >= 8 && digits * 10 >= s.length() * 6) {
            return true;
        }
        return s.length() >= 10 && s.matches("[0-9A-Za-z_\\-/]+") && digits >= 6;
    }

    static String stripOrderTail(String merchant) {
        if (merchant == null || merchant.isBlank()) {
            return "";
        }
        String s = merchant.trim();
        if (s.length() > 16 && s.matches(".*[0-9A-Za-z_\\-]{16,}")) {
            s = s.replaceFirst("[0-9A-Za-z_\\-]{16,}$", "");
        }
        return s.trim();
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.replace('\u00a0', ' ').trim();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }
}

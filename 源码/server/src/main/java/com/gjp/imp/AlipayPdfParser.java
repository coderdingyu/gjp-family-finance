package com.gjp.imp;

import com.gjp.common.AppTime;
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
 * 解析支付宝「交易流水证明」PDF。按阅读坐标抽字后，每笔以 收入/支出/不计 开头。
 */
public final class AlipayPdfParser {

    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern AMOUNT_BEFORE_ORDER = Pattern.compile("(?<![0-9.])(\\d{1,7}\\.\\d{2})\\s+\\d{8,}");
    private static final Pattern START = Pattern.compile("^(收入|支出|不计)");

    private AlipayPdfParser() {
    }

    public static boolean looksLike(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("支付宝") && (text.contains("交易流水证明")
                || text.contains("电子客户回单") || text.contains("支付宝支付科技"));
    }

    public static DifyParseResult parse(String text) {
        DifyParseResult result = new DifyParseResult();
        List<Map<String, Object>> records = new ArrayList<>();
        for (List<String> block : blocks(text)) {
            Map<String, Object> rec = parseBlock(block);
            if (rec != null) {
                records.add(rec);
            }
        }
        if (records.isEmpty()) {
            result.setRelevant(false);
            result.setReason("这是支付宝流水证明，但没有抽出有效流水");
            return result;
        }
        result.setRelevant(true);
        result.setRecords(records);
        return result;
    }

    private static List<List<String>> blocks(String text) {
        List<List<String>> out = new ArrayList<>();
        List<String> current = null;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.replace('\u00a0', ' ').trim();
            if (line.isEmpty() || skipNoise(line)) {
                continue;
            }
            if (START.matcher(line).find()) {
                if (current != null) {
                    out.add(current);
                }
                current = new ArrayList<>();
                current.add(line);
            } else if (current != null) {
                current.add(line);
            }
        }
        if (current != null) {
            out.add(current);
        }
        return out;
    }

    private static boolean skipNoise(String line) {
        return line.startsWith("第 ") || line.startsWith("特别提示")
                || line.startsWith("收/支") || line.startsWith("币种")
                || line.startsWith("交易时间段") || line.startsWith("交易类型：")
                || line.startsWith("本证明") || line.startsWith("支付宝支付科技")
                || line.contains("专用章") || line.matches("\\d+\\..*");
    }

    private static Map<String, Object> parseBlock(List<String> block) {
        String joined = String.join(" ", block).replaceAll("\\s+", " ").trim();
        if (joined.startsWith("不计")) {
            return null;
        }
        Integer type = BillTextParser.parseType(joined.startsWith("收入") ? "收入" : "支出");
        if (type == null) {
            return null;
        }
        Matcher dateM = DATE.matcher(joined);
        if (!dateM.find()) {
            return null;
        }
        LocalDate date = BillTextParser.parseDate(dateM.group(1));
        if (date == null || date.isAfter(AppTime.today())) {
            return null;
        }
        Matcher amtM = AMOUNT_BEFORE_ORDER.matcher(joined);
        if (!amtM.find()) {
            return null;
        }
        BigDecimal amount = BillTextParser.parseAmount(amtM.group(1));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String rest = joined.replaceFirst("^(收入|支出)\\s*", "");
        String[] toks = rest.split("\\s+");
        if (toks.length == 0) {
            return null;
        }
        String merchant = toks[0];
        String pay = findPay(toks);
        String remark = takeRemark(toks);
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", type);
        rec.put("amount", amount);
        rec.put("recordDate", date.toString());
        rec.put("categoryName", "");
        rec.put("merchant", merchant);
        rec.put("area", "");
        rec.put("payMethod", BillTextParser.normalizePay(pay));
        rec.put("isGift", 0);
        rec.put("remark", remark);
        return rec;
    }

    private static String findPay(String[] toks) {
        for (String tok : toks) {
            if (looksPay(tok)) {
                return tok;
            }
        }
        return "";
    }

    private static boolean looksPay(String tok) {
        return tok.contains("花呗") || tok.contains("储蓄") || tok.contains("信用")
                || tok.contains("余额") || tok.contains("支付宝") || tok.contains("骑行卡")
                || tok.contains("零钱") || tok.contains("银行卡");
    }

    private static String takeRemark(String[] toks) {
        int end = toks.length;
        for (int i = 1; i < toks.length; i++) {
            if (looksPay(toks[i]) || toks[i].matches("\\d+\\.\\d{2}") || toks[i].matches("\\d{8,}")) {
                end = i;
                break;
            }
        }
        if (end <= 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < end; i++) {
            sb.append(toks[i]);
        }
        String s = sb.toString();
        return s.length() <= 80 ? s : s.substring(0, 80);
    }
}

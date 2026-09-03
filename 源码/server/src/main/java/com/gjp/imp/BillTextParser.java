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
 * 通用表格账单抽取：按「日期 / 金额 / 收支 / 对方」语义认列，不绑死微信或支付宝表头。
 * 表头对不上时，用前几行单元格反推；整表认不出再按行扫日期+金额。
 */
public final class BillTextParser {

    private static final DateTimeFormatter[] DATES = {
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:m:s")
    };
    private static final Pattern MD = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern MONEY = Pattern.compile("^[+\\-(]?\\d{1,12}(\\.\\d{1,2})?\\)?$");
    private static final Pattern LOOSE_DATE = Pattern.compile(
            "(\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?)");
    private static final Pattern EN_DATE = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+(\\d{4})(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?\\b");
    private static final Pattern LEAD_MONEY = Pattern.compile("^[+\\-(]?(\\d{1,12}(?:\\.\\d{1,2})?)");
    private static final Pattern MONEY_SUFFIX = Pattern.compile(
            "(?i)^(元|圆|整|人民币|cny|rmb|[（(]).*");
    private static final String[] EN_MON = {
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    };
    private static final Pattern LOOSE_MONEY = Pattern.compile(
            "(?<![A-Za-z0-9])([+\\-]?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})|[+\\-]?\\d{1,7}\\.\\d{2}|[+\\-]?\\d{1,7})(?:元|人民币|CNY|RMB)?");
    private static final Pattern TYPE_WORD = Pattern.compile("收入|支出|不计|income|expense", Pattern.CASE_INSENSITIVE);

    /** 0 date, 1 amount, 2 income, 3 expense, 4 type, 5 category, 6 merchant, 7 area, 8 pay, 9 gift, 10 remark, 11 product, 12 status, 13 orderNo */
    private static final int COLS = 14;

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
        DifyParseResult table = parseTable(lines);
        if (table.isRelevant()) {
            return table;
        }
        DifyParseResult loose = scanLines(lines);
        if (loose.isRelevant()) {
            return loose;
        }
        return table;
    }

    /** 给 Excel 去说明段用：这一行像不像账单表头。 */
    public static boolean isBillHeader(String line) {
        if (line == null || line.isBlank() || looksUnrelated(line)) {
            return false;
        }
        String[] headers = split(line);
        if (headers.length < 2) {
            return false;
        }
        int[] cols = mapColumns(headers);
        int signals = 0;
        if (cols[0] >= 0) {
            signals++;
        }
        if (hasAmount(cols)) {
            signals++;
        }
        if (cols[4] >= 0) {
            signals++;
        }
        if (cols[6] >= 0) {
            signals++;
        }
        return signals >= 2;
    }

    public static boolean looksBillText(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("金额") || t.contains("人民币") || t.contains("amount")
                || t.contains("收/支") || t.contains("收支") || t.contains("进出")
                || t.contains("交易时间") || t.contains("交易日") || t.contains("借方")
                || t.contains("支付宝") || t.contains("微信") || t.contains("账单");
    }

    private static DifyParseResult parseTable(String[] lines) {
        DifyParseResult result = new DifyParseResult();
        int headerAt = findHeader(lines);
        if (headerAt < 0) {
            // 列名是黑话时仍可能从数据行反推出日期+金额列
            headerAt = 0;
        }
        String[] headers = split(lines[headerAt]);
        int[] cols = mapColumns(headers);
        List<String[]> data = new ArrayList<>();
        for (int i = headerAt + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                data.add(split(line));
            }
        }
        inferMissing(cols, data, headers.length);
        if (cols[0] < 0 && !hasAmount(cols) && cols[4] < 0) {
            result.setRelevant(false);
            result.setReason("表格里找不到日期或金额列，不像家庭账单");
            return result;
        }
        if (!hasAmount(cols) && cols[4] < 0) {
            result.setRelevant(false);
            result.setReason("表格里找不到金额或收支列，不像家庭账单");
            return result;
        }
        List<Map<String, Object>> records = new ArrayList<>();
        boolean defaultDate = cols[0] < 0;
        for (String[] cells : data) {
            Map<String, Object> rec = parseRow(cells, cols, defaultDate);
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

    private static DifyParseResult scanLines(String[] lines) {
        DifyParseResult result = new DifyParseResult();
        List<Map<String, Object>> records = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.isBlank() || isBillHeader(raw) || looksUnrelated(raw)) {
                continue;
            }
            Map<String, Object> rec = parseLooseLine(raw.trim());
            if (rec != null) {
                records.add(rec);
            }
        }
        if (records.isEmpty()) {
            result.setRelevant(false);
            result.setReason("文字里找不到成对的日期和金额");
            return result;
        }
        result.setRelevant(true);
        result.setRecords(records);
        return result;
    }

    private static Map<String, Object> parseLooseLine(String line) {
        Matcher dateM = LOOSE_DATE.matcher(line);
        Matcher enDateM = EN_DATE.matcher(line);
        String dateTok = null;
        if (dateM.find()) {
            dateTok = dateM.group(1);
        } else if (enDateM.find()) {
            dateTok = enDateM.group();
        } else {
            return null;
        }
        LocalDate date = parseDate(dateTok);
        if (date == null || date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(2000, 1, 1))) {
            return null;
        }
        Integer type = parseType(line);
        Matcher moneyM = LOOSE_MONEY.matcher(line);
        BigDecimal amount = null;
        int moneyAt = -1;
        while (moneyM.find()) {
            String tok = moneyM.group(1);
            if (tok.equals(dateTok.replaceAll("\\D", "")) || tok.length() >= 8) {
                continue;
            }
            BigDecimal n = parseAmount(tok);
            if (n != null && n.compareTo(BigDecimal.ZERO) > 0) {
                amount = n;
                moneyAt = moneyM.start();
            }
        }
        if (amount == null) {
            return null;
        }
        if (type == null && line.contains("-")) {
            type = 2;
        }
        if (type == null) {
            type = 2;
        }
        String merchant = stripLoose(line, dateTok, moneyAt >= 0 ? line.substring(moneyAt, Math.min(line.length(), moneyAt + 16)) : "");
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", type);
        rec.put("amount", amount);
        rec.put("recordDate", date.toString());
        rec.put("categoryName", "");
        rec.put("merchant", trimTo(merchant, 100));
        rec.put("area", "");
        rec.put("payMethod", normalizePay(line));
        rec.put("isGift", parseGift(line));
        rec.put("remark", "");
        return rec;
    }

    private static String stripLoose(String line, String dateTok, String moneyTok) {
        String s = line.replace(dateTok, " ");
        if (moneyTok != null && !moneyTok.isBlank()) {
            s = s.replace(moneyTok, " ");
        }
        s = TYPE_WORD.matcher(s).replaceAll(" ");
        s = s.replaceAll("(?i)元|人民币|cny|rmb|¥|￥", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static int findHeader(String[] lines) {
        int limit = Math.min(lines.length, 40);
        int best = -1;
        int bestScore = 0;
        for (int i = 0; i < limit; i++) {
            if (looksUnrelated(lines[i])) {
                continue;
            }
            int score = headerScore(split(lines[i]));
            if (score > bestScore && isBillHeader(lines[i])) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static int headerScore(String[] headers) {
        int[] cols = mapColumns(headers);
        int n = 0;
        if (cols[0] >= 0) {
            n += 2;
        }
        if (hasAmount(cols)) {
            n += 2;
        }
        if (cols[4] >= 0) {
            n += 2;
        }
        if (cols[6] >= 0) {
            n++;
        }
        if (cols[8] >= 0) {
            n++;
        }
        return n;
    }

    private static int[] mapColumns(String[] headers) {
        int[] cols = new int[COLS];
        java.util.Arrays.fill(cols, -1);
        for (int i = 0; i < headers.length; i++) {
            String h = compact(headers[i]).toLowerCase(Locale.ROOT);
            if (h.isEmpty() || skipHeader(h)) {
                continue;
            }
            int role = bestRole(h);
            if (role >= 0 && cols[role] < 0) {
                cols[role] = i;
            }
        }
        return cols;
    }

    private static boolean skipHeader(String h) {
        return (h.contains("账号") || h.contains("account"))
                && !h.contains("订单") && !h.contains("单号");
    }

    private static int bestRole(String h) {
        int best = -1;
        int score = 0;
        int[][] roles = {
                {0, scoreDate(h)},
                {2, scoreIncomeAmount(h)},
                {3, scoreExpenseAmount(h)},
                {1, scoreAmount(h)},
                {4, scoreType(h)},
                {5, scoreCategory(h)},
                {8, scorePay(h)},
                {9, scoreGift(h)},
                {11, scoreProduct(h)},
                {12, scoreStatus(h)},
                {6, scoreMerchant(h)},
                {7, scoreArea(h)},
                {10, scoreRemark(h)},
                {13, scoreOrderNo(h)}
        };
        for (int[] pair : roles) {
            if (pair[1] > score) {
                score = pair[1];
                best = pair[0];
            }
        }
        return score >= 2 ? best : -1;
    }

    private static int scoreDate(String h) {
        if (h.contains("交易时间") || h.contains("交易日") || h.contains("消费日")
                || h.contains("发生日") || h.contains("记账日") || h.contains("入账日")
                || h.contains("发生时间") || h.contains("过账") || h.contains("时点")
                || h.equals("date") || h.contains("datetime")
                || h.equals("time")) {
            return 4;
        }
        if (h.contains("日期") || h.contains("时间")) {
            return 3;
        }
        return 0;
    }

    private static int scoreIncomeAmount(String h) {
        if (h.contains("收入金额") || h.contains("inamount") || h.contains("贷方") || h.equals("贷")) {
            return 5;
        }
        if (h.equals("收入") || h.equals("income")) {
            return 4;
        }
        return 0;
    }

    private static int scoreExpenseAmount(String h) {
        if (h.contains("支出金额") || h.contains("outamount") || h.contains("借方") || h.equals("借")) {
            return 5;
        }
        if (h.equals("支出") || h.equals("expense")) {
            return 4;
        }
        return 0;
    }

    private static int scoreAmount(String h) {
        if (h.contains("收入金额") || h.contains("支出金额") || h.contains("借方") || h.contains("贷方")) {
            return 0;
        }
        if (h.contains("金额") || h.equals("amount") || h.equals("money") || h.equals("人民币")
                || h.equals("rmb") || h.equals("cny") || h.contains("发生额") || h.contains("交易额")
                || h.contains("实付") || h.contains("数额") || h.equals("元") || h.contains("价税合计")
                || h.equals("票面") || h.contains("票面额")) {
            return 4;
        }
        if (h.endsWith("元") && h.contains("金")) {
            return 3;
        }
        return 0;
    }

    private static int scoreType(String h) {
        if (h.contains("交易类型") || h.contains("交易方式") || h.contains("交易分类")) {
            return 0;
        }
        if (h.contains("收/支") || h.contains("收／支") || h.equals("收支") || h.contains("进出方向")
                || h.equals("进出") || h.contains("借贷") || h.equals("inout") || h.contains("in/out")) {
            return 5;
        }
        if (h.equals("类型") || h.equals("type") || h.equals("方向") || h.contains("方向码")
                || h.contains("借贷符")) {
            return 3;
        }
        if (h.contains("类型") && !h.contains("交易")) {
            return 2;
        }
        return 0;
    }

    private static int scoreCategory(String h) {
        if (h.contains("分类") || h.contains("类别") || h.equals("category") || h.equals("科室")
                || h.equals("科目")) {
            return 3;
        }
        return 0;
    }

    private static int scoreMerchant(String h) {
        if (h.contains("交易对方") || h.contains("对方名称") || h.contains("对手方") || h.equals("对手")
                || h.contains("商家") || h.contains("商户") || h.contains("店铺")
                || h.equals("merchant") || h.equals("payee") || h.equals("description")
                || h.equals("摘要") || h.contains("户名") || h.contains("收款方") || h.contains("付款方")) {
            return 4;
        }
        if (h.contains("对方") || h.contains("shop") || h.contains("往来")) {
            return 3;
        }
        return 0;
    }

    private static int scoreArea(String h) {
        if (h.contains("片区") || h.contains("地区") || h.contains("城市") || h.equals("area")) {
            return 3;
        }
        return 0;
    }

    private static int scorePay(String h) {
        if (h.contains("收/付款方式") || h.contains("支付方式") || h.contains("付款方式")
                || h.contains("资金渠道") || h.equals("paymethod") || h.equals("payment")) {
            return 4;
        }
        if (h.contains("支付") || h.contains("付款") || h.contains("渠道")
                || (h.contains("pay") && !h.contains("payee"))) {
            return 3;
        }
        return 0;
    }

    private static int scoreGift(String h) {
        if (h.contains("人情") || h.contains("礼金") || h.equals("gift") || h.contains("isgift")) {
            return 4;
        }
        return 0;
    }

    private static int scoreProduct(String h) {
        if (h.contains("商品") || h.contains("品名") || h.contains("项目名称") || h.equals("item")) {
            return 3;
        }
        return 0;
    }

    private static int scoreStatus(String h) {
        if (h.contains("状态") || h.equals("status")) {
            return 3;
        }
        return 0;
    }

    private static int scoreRemark(String h) {
        if (h.contains("商品说明") || h.contains("订单")) {
            return 0;
        }
        if (h.contains("备注") || h.contains("说明") || h.equals("remark") || h.equals("note") || h.equals("memo")) {
            return 3;
        }
        return 0;
    }

    private static int scoreOrderNo(String h) {
        if (h.contains("交易订单号") || h.contains("交易单号")) {
            return 6;
        }
        if (h.contains("商家订单号") || h.contains("商户单号") || h.contains("商单号")) {
            return 5;
        }
        if (h.contains("订单号") || h.equals("单号") || h.contains("流水号") || h.equals("orderno")
                || h.equals("orderid") || h.contains("transactionid")) {
            return 4;
        }
        return 0;
    }

    private static void inferMissing(int[] cols, List<String[]> rows, int headerWidth) {
        if (rows.isEmpty()) {
            return;
        }
        int width = headerWidth;
        for (String[] row : rows) {
            width = Math.max(width, row.length);
        }
        int sample = Math.min(rows.size(), 30);
        int[] dateHits = new int[width];
        int[] moneyHits = new int[width];
        int[] typeHits = new int[width];
        int[] textLen = new int[width];
        for (int r = 0; r < sample; r++) {
            String[] row = rows.get(r);
            for (int c = 0; c < width; c++) {
                String v = cell(row, c);
                if (v.isEmpty()) {
                    continue;
                }
                if (parseDate(v) != null) {
                    dateHits[c]++;
                }
                if (parseAmount(v) != null) {
                    moneyHits[c]++;
                }
                if (parseType(v) != null) {
                    typeHits[c]++;
                }
                if (v.length() >= 2 && parseDate(v) == null && parseAmount(v) == null && parseType(v) == null) {
                    textLen[c] += v.length();
                }
            }
        }
        boolean[] taken = new boolean[width];
        for (int col : cols) {
            if (col >= 0 && col < width) {
                taken[col] = true;
            }
        }
        if (cols[0] < 0) {
            cols[0] = bestHit(dateHits, taken, Math.max(2, sample / 3));
            if (cols[0] >= 0) {
                taken[cols[0]] = true;
            }
        }
        if (!hasAmount(cols)) {
            cols[1] = bestHit(moneyHits, taken, Math.max(2, sample / 3));
            if (cols[1] >= 0) {
                taken[cols[1]] = true;
            }
        }
        if (cols[4] < 0 && cols[2] < 0 && cols[3] < 0) {
            cols[4] = bestHit(typeHits, taken, Math.max(2, sample / 3));
            if (cols[4] >= 0) {
                taken[cols[4]] = true;
            }
        }
        if (cols[6] < 0) {
            int best = -1;
            int bestL = 8;
            for (int c = 0; c < width; c++) {
                if (!taken[c] && textLen[c] > bestL) {
                    bestL = textLen[c];
                    best = c;
                }
            }
            cols[6] = best;
        }
    }

    private static int bestHit(int[] hits, boolean[] taken, int min) {
        int best = -1;
        int bestN = min - 1;
        for (int i = 0; i < hits.length; i++) {
            if (!taken[i] && hits[i] > bestN) {
                bestN = hits[i];
                best = i;
            }
        }
        return best;
    }

    private static Map<String, Object> parseRow(String[] cells, int[] cols, boolean defaultDate) {
        LocalDate date = parseDate(cell(cells, cols[0]));
        if (date == null && defaultDate) {
            date = LocalDate.now();
        }
        if (date == null) {
            for (String cell : cells) {
                date = parseDate(cell);
                if (date != null) {
                    break;
                }
            }
        }
        if (date == null || date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.of(2000, 1, 1))) {
            return null;
        }
        if (isNeutral(cell(cells, cols[4])) || isClosed(cell(cells, cols[12]))) {
            return null;
        }
        int type = 2;
        BigDecimal amount = null;
        boolean signed = false;
        BigDecimal income = parseAmount(cell(cells, cols[2]));
        BigDecimal expense = parseAmount(cell(cells, cols[3]));
        if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
            type = 1;
            amount = income;
        } else if (expense != null && expense.compareTo(BigDecimal.ZERO) > 0) {
            type = 2;
            amount = expense;
        } else {
            String rawAmt = cell(cells, cols[1]);
            signed = looksNegative(rawAmt);
            amount = parseAmount(rawAmt);
            Integer typed = parseType(cell(cells, cols[4]));
            if (typed != null) {
                type = typed;
            } else if (signed) {
                type = 2;
            } else if (looksPositive(rawAmt) && cols[4] < 0) {
                type = 1;
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
        rec.put("merchant", firstMeaningful(cell(cells, cols[6]), remark));
        rec.put("area", cell(cells, cols[7]));
        rec.put("payMethod", normalizePay(cell(cells, cols[8])));
        rec.put("isGift", Math.max(parseGift(cell(cells, cols[9])),
                Math.max(parseGift(cell(cells, cols[5])), parseGift(cell(cells, cols[6])))));
        rec.put("remark", remark);
        String orderNo = cell(cells, cols[13]);
        rec.put("orderNo", orderNo == null || orderNo.isBlank() ? "" : orderNo.trim());
        return rec;
    }

    private static boolean hasAmount(int[] cols) {
        return cols[1] >= 0 || cols[2] >= 0 || cols[3] >= 0;
    }

    private static boolean looksUnrelated(String line) {
        String h = compact(line);
        return h.contains("学号") || h.contains("成绩") || h.contains("课程")
                || (h.contains("姓名") && !h.contains("金额") && !h.contains("日期") && !h.contains("收支"));
    }

    private static boolean isNeutral(String raw) {
        if (raw == null) {
            return false;
        }
        String s = compact(raw);
        return s.equals("/") || s.equals("／") || s.contains("中性") || s.contains("不计");
    }

    private static boolean isClosed(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = compact(raw);
        return s.contains("关闭") || s.contains("失败") || s.contains("已撤销");
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
        Matcher en = EN_DATE.matcher(s);
        if (en.find()) {
            int day = Integer.parseInt(en.group(1));
            int month = enMonth(en.group(2));
            int year = Integer.parseInt(en.group(3));
            try {
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (s.length() >= 10 && (s.charAt(4) == '-' || s.charAt(4) == '/' || s.charAt(4) == '.')) {
            String head = s.substring(0, 10).replace('/', '-').replace('.', '-');
            try {
                return LocalDate.parse(head);
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        String compactDate = s.replace("年", "-").replace("月", "-").replace("日", "");
        for (DateTimeFormatter fmt : DATES) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDate.parse(compactDate, fmt);
                } catch (DateTimeParseException ignored2) {
                    // try next
                }
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
        s = s.replaceAll("(?i)(人民币|cny|rmb|usd|元|圆)$", "");
        s = s.replaceAll("(?i)^(人民币|cny|rmb|usd)", "");
        if (s.startsWith("(") && s.endsWith(")") || s.startsWith("（") && s.endsWith("）")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.startsWith("+") || s.startsWith("-")) {
            s = s.substring(1);
        }
        if (MONEY.matcher(s).matches()) {
            try {
                return new BigDecimal(s).abs();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        Matcher lead = LEAD_MONEY.matcher(s);
        if (lead.find()) {
            String rest = s.substring(lead.end());
            if (rest.isEmpty() || MONEY_SUFFIX.matcher(rest).matches()) {
                try {
                    return new BigDecimal(lead.group(1)).abs();
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    static Integer parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = compact(raw).toLowerCase(Locale.ROOT);
        if (s.contains("不计") || s.contains("中性")) {
            return null;
        }
        if (s.contains("收入") || s.contains("income") || s.equals("in") || s.equals("1")
                || s.equals("贷") || s.equals("贷方") || s.equals("credit") || s.equals("cr")) {
            return 1;
        }
        if (s.contains("支出") || s.contains("expense") || s.equals("out") || s.equals("2")
                || s.equals("借") || s.equals("借方") || s.equals("debit") || s.equals("dr")) {
            return 2;
        }
        if (s.equals("收") || s.startsWith("收")) {
            return 1;
        }
        if (s.equals("支") || s.startsWith("支")) {
            return 2;
        }
        return null;
    }

    static int parseGift(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String s = compact(raw).toLowerCase(Locale.ROOT);
        if (s.equals("1") || s.equals("是") || s.equals("true") || s.contains("礼") || s.contains("红包")
                || s.contains("请客") || s.contains("份子")) {
            return 1;
        }
        return 0;
    }

    static String normalizePay(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = compact(raw);
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.contains("微信") || s.contains("零钱") || lower.contains("wechat") || lower.contains("weixin")) {
            return "微信";
        }
        if (s.contains("花呗") || s.contains("余额") || s.contains("红包") || s.contains("支付宝")
                || lower.contains("alipay")) {
            return "支付宝";
        }
        if (s.contains("现金") || lower.contains("cash")) {
            return "现金";
        }
        if (s.contains("银行卡") || s.contains("信用卡") || s.contains("储蓄卡") || s.contains("借记卡")
                || s.contains("云闪付") || lower.contains("unionpay")) {
            return "银行卡";
        }
        if (s.contains("其他") || lower.contains("misc") || lower.contains("wallet")
                || lower.contains("other")) {
            return "其他";
        }
        return "";
    }

    static String[] split(String line) {
        if (line == null) {
            return new String[0];
        }
        if (line.contains("\t")) {
            return line.split("\t", -1);
        }
        int commas = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ',') {
                commas++;
            }
        }
        if (commas >= 2) {
            return ExcelTextExtractor.splitCsvCells(line);
        }
        if (line.matches(".*\\s{2,}.*")) {
            return line.trim().split("\\s{2,}", -1);
        }
        String[] ws = line.trim().split("\\s+");
        if (ws.length >= 4) {
            return ws;
        }
        return new String[]{line};
    }

    private static boolean looksNegative(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.trim();
        return s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"))
                || (s.startsWith("（") && s.endsWith("）"));
    }

    private static boolean looksPositive(String raw) {
        return raw != null && raw.trim().startsWith("+");
    }

    private static String cell(String[] cells, int idx) {
        if (idx < 0 || idx >= cells.length) {
            return "";
        }
        return cells[idx] == null ? "" : cells[idx].trim();
    }

    private static int enMonth(String raw) {
        String m = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < EN_MON.length; i++) {
            if (m.startsWith(EN_MON[i])) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private static String trimTo(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}

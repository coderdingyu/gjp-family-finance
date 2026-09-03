package com.gjp.record;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjp.dify.DifyClient;
import com.gjp.entity.Category;
import com.gjp.entity.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把自然语言问题收成 RecordQuery。
 * 智能体成功时解析它的 JSON；失败时走本机关键字 / 日期 / 金额 / 分类。
 */
public final class RecordAskParser {

    public static final int MAX_QUESTION_CHARS = 2000;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> PAY_METHODS = List.of("现金", "微信", "支付宝", "银行卡", "其他");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})[-年](\\d{1,2})月?");
    private static final Pattern CN_MONTH = Pattern.compile("(\\d{1,2})月");
    private static final Pattern MIN_AMOUNT = Pattern.compile(
            "(?:超过|大于|高于|不少于|至少|≥|>=)\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern MIN_AMOUNT_SUFFIX = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*元?(?:以上|及以上)");
    private static final Pattern MAX_AMOUNT = Pattern.compile(
            "(?:低于|小于|少于|不超过|不足|≤|<=)\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern MAX_AMOUNT_SUFFIX = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*元?(?:以下|及以下)");
    private static final Pattern RANGE_AMOUNT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*[-~到至]\\s*(\\d+(?:\\.\\d+)?)\\s*元?");

    private RecordAskParser() {
    }

    public static String clip(String q) {
        if (q == null) {
            return "";
        }
        String text = q.strip();
        if (text.length() > MAX_QUESTION_CHARS) {
            return text.substring(0, MAX_QUESTION_CHARS);
        }
        return text;
    }

    public static AskDraft parseDify(String raw) throws Exception {
        JsonNode node = MAPPER.readTree(DifyClient.extractJson(raw));
        String answer = text(node, "answer");
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("智能体未返回 answer");
        }
        RecordQuery query = new RecordQuery();
        String categoryName = null;
        JsonNode q = node.get("query");
        if (q != null && q.isObject()) {
            query.setType(intOrNull(q, "type"));
            query.setMemberId(longOrNull(q, "memberId"));
            query.setCategoryId(longOrNull(q, "categoryId"));
            categoryName = text(q, "categoryName");
            query.setStartDate(dateOrNull(q, "startDate"));
            query.setEndDate(dateOrNull(q, "endDate"));
            String keyword = text(q, "keyword");
            query.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
            String pay = text(q, "payMethod");
            if (pay != null && PAY_METHODS.contains(pay)) {
                query.setPayMethod(pay);
            }
            String area = text(q, "area");
            query.setArea(area == null || area.isBlank() ? null : area.trim());
            query.setIsGift(intOrNull(q, "isGift"));
            query.setMinAmount(decimalOrNull(q, "minAmount"));
            query.setMaxAmount(decimalOrNull(q, "maxAmount"));
        }
        return new AskDraft(answer.trim(), query, categoryName);
    }

    public static AskDraft parseLocal(String q, List<Category> cats, List<Member> members) {
        RecordQuery query = new RecordQuery();
        String text = q == null ? "" : q;

        if (text.contains("收入") && !text.contains("支出")) {
            query.setType(1);
        } else if (text.contains("支出") || text.contains("开销") || text.contains("消费")
                || text.contains("花了") || text.contains("花费")) {
            query.setType(2);
        }

        if (text.contains("人情") || text.contains("礼金") || text.contains("红包")
                || text.contains("随份子") || text.contains("礼尚往来")) {
            query.setIsGift(1);
        }

        for (String p : PAY_METHODS) {
            if (text.contains(p)) {
                query.setPayMethod(p);
                break;
            }
        }

        parseAmounts(text, query);
        parseDates(text, query);
        bindCategoryName(query, longestMatch(text, categoryNames(cats)), cats);
        Long memberId = longestMember(text, members);
        if (memberId != null) {
            query.setMemberId(memberId);
        }
        String keyword = leftoverKeyword(text, cats, members, query);
        query.setKeyword(keyword);

        return new AskDraft(localAnswer(query), query, null);
    }

    public static void bindCategoryName(RecordQuery query, String categoryName, List<Category> cats) {
        if (query.getCategoryId() != null || categoryName == null || categoryName.isBlank() || cats == null) {
            return;
        }
        String name = categoryName.trim();
        Category exact = null;
        Category fuzzy = null;
        for (Category c : cats) {
            if (c.getCategoryName() == null) {
                continue;
            }
            if (c.getCategoryName().equals(name)) {
                exact = c;
                break;
            }
            if (fuzzy == null && (c.getCategoryName().contains(name) || name.contains(c.getCategoryName()))) {
                fuzzy = c;
            }
        }
        Category hit = exact != null ? exact : fuzzy;
        if (hit != null) {
            query.setCategoryId(hit.getId());
        }
    }

    public static Map<String, Object> publicQuery(RecordQuery q) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", q.getType());
        map.put("memberId", q.getMemberId());
        map.put("categoryId", q.getCategoryId());
        map.put("startDate", q.getStartDate());
        map.put("endDate", q.getEndDate());
        map.put("keyword", q.getKeyword());
        map.put("payMethod", q.getPayMethod());
        map.put("area", q.getArea());
        map.put("isGift", q.getIsGift());
        map.put("minAmount", q.getMinAmount());
        map.put("maxAmount", q.getMaxAmount());
        return map;
    }

    static String localAnswer(RecordQuery q) {
        List<String> bits = new ArrayList<>();
        if (q.getType() != null) {
            bits.add(q.getType() == 1 ? "收入" : "支出");
        }
        if (q.getStartDate() != null || q.getEndDate() != null) {
            bits.add("日期 " + (q.getStartDate() == null ? "*" : q.getStartDate())
                    + " ~ " + (q.getEndDate() == null ? "*" : q.getEndDate()));
        }
        if (q.getMinAmount() != null) {
            bits.add("金额≥" + q.getMinAmount());
        }
        if (q.getMaxAmount() != null) {
            bits.add("金额≤" + q.getMaxAmount());
        }
        if (q.getPayMethod() != null) {
            bits.add(q.getPayMethod());
        }
        if (q.getIsGift() != null && q.getIsGift() == 1) {
            bits.add("人情往来");
        }
        if (q.getKeyword() != null) {
            bits.add("关键字「" + q.getKeyword() + "」");
        }
        if (q.getCategoryId() != null) {
            bits.add("已按分类筛选");
        }
        if (bits.isEmpty()) {
            return "已按你的问题在权限范围内筛选流水。";
        }
        return "已按" + String.join("、", bits) + "筛选流水。";
    }

    private static void parseAmounts(String text, RecordQuery query) {
        Matcher range = RANGE_AMOUNT.matcher(text);
        if (range.find()) {
            query.setMinAmount(new BigDecimal(range.group(1)));
            query.setMaxAmount(new BigDecimal(range.group(2)));
            return;
        }
        Matcher min = MIN_AMOUNT.matcher(text);
        if (min.find()) {
            query.setMinAmount(new BigDecimal(min.group(1)));
        } else {
            Matcher min2 = MIN_AMOUNT_SUFFIX.matcher(text);
            if (min2.find()) {
                query.setMinAmount(new BigDecimal(min2.group(1)));
            }
        }
        Matcher max = MAX_AMOUNT.matcher(text);
        if (max.find()) {
            query.setMaxAmount(new BigDecimal(max.group(1)));
        } else {
            Matcher max2 = MAX_AMOUNT_SUFFIX.matcher(text);
            if (max2.find()) {
                query.setMaxAmount(new BigDecimal(max2.group(1)));
            }
        }
    }

    private static void parseDates(String text, RecordQuery query) {
        YearMonth now = YearMonth.now();
        LocalDate today = LocalDate.now();
        if (text.contains("上个月") || text.contains("上月")) {
            YearMonth prev = now.minusMonths(1);
            query.setStartDate(prev.atDay(1));
            query.setEndDate(prev.atEndOfMonth());
            return;
        }
        if (text.contains("本月") || text.contains("这个月") || text.contains("当月")) {
            query.setStartDate(now.atDay(1));
            query.setEndDate(today.isBefore(now.atEndOfMonth()) ? today : now.atEndOfMonth());
            return;
        }
        if (text.contains("今年")) {
            query.setStartDate(LocalDate.of(now.getYear(), 1, 1));
            query.setEndDate(today);
            return;
        }
        if (text.contains("去年")) {
            int y = now.getYear() - 1;
            query.setStartDate(LocalDate.of(y, 1, 1));
            query.setEndDate(LocalDate.of(y, 12, 31));
            return;
        }
        if (text.contains("今天")) {
            query.setStartDate(today);
            query.setEndDate(today);
            return;
        }
        Matcher iso = ISO_DATE.matcher(text);
        LocalDate first = null;
        LocalDate second = null;
        while (iso.find()) {
            LocalDate d = LocalDate.parse(iso.group(1));
            if (first == null) {
                first = d;
            } else {
                second = d;
                break;
            }
        }
        if (first != null && second != null) {
            if (first.isAfter(second)) {
                LocalDate tmp = first;
                first = second;
                second = tmp;
            }
            query.setStartDate(first);
            query.setEndDate(second);
            return;
        }
        if (first != null) {
            query.setStartDate(first);
            query.setEndDate(first);
            return;
        }
        Matcher ym = YEAR_MONTH.matcher(text);
        if (ym.find()) {
            int year = Integer.parseInt(ym.group(1));
            int month = Integer.parseInt(ym.group(2));
            if (month >= 1 && month <= 12) {
                YearMonth m = YearMonth.of(year, month);
                query.setStartDate(m.atDay(1));
                query.setEndDate(m.atEndOfMonth());
                return;
            }
        }
        Matcher cm = CN_MONTH.matcher(text);
        if (cm.find()) {
            int month = Integer.parseInt(cm.group(1));
            if (month >= 1 && month <= 12) {
                YearMonth m = YearMonth.of(now.getYear(), month);
                query.setStartDate(m.atDay(1));
                query.setEndDate(m.atEndOfMonth());
            }
        }
    }

    private static List<String> categoryNames(List<Category> cats) {
        List<String> names = new ArrayList<>();
        if (cats == null) {
            return names;
        }
        for (Category c : cats) {
            if (c.getCategoryName() != null && !c.getCategoryName().isBlank()) {
                names.add(c.getCategoryName());
            }
        }
        names.sort(Comparator.comparingInt(String::length).reversed());
        return names;
    }

    private static String longestMatch(String text, List<String> names) {
        for (String name : names) {
            if (name.length() >= 2 && text.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private static Long longestMember(String text, List<Member> members) {
        if (members == null) {
            return null;
        }
        Member hit = null;
        for (Member m : members) {
            if (m.getMemberName() == null || m.getMemberName().length() < 2) {
                continue;
            }
            if (text.contains(m.getMemberName())
                    && (hit == null || m.getMemberName().length() > hit.getMemberName().length())) {
                hit = m;
            }
        }
        return hit == null ? null : hit.getId();
    }

    private static String leftoverKeyword(String text, List<Category> cats, List<Member> members,
                                          RecordQuery query) {
        String leftover = text;
        leftover = leftover.replaceAll("\\d{4}-\\d{2}-\\d{2}", " ");
        leftover = leftover.replaceAll("\\d+(?:\\.\\d+)?\\s*元?", " ");
        leftover = leftover.replaceAll("去年|今年|上个月|上月|本月|这个月|当月|今天", " ");
        leftover = leftover.replaceAll("收入|支出|开销|消费|花了|花费|人情|礼金|红包|随份子|礼尚往来", " ");
        leftover = leftover.replaceAll("超过|大于|高于|低于|小于|少于|不少于|至少|以上|以下|及以上|及以下", " ");
        leftover = leftover.replaceAll("查询|看看|帮我|请问|一下|多少|什么|哪些|流水|账单|记录|筛选", " ");
        for (String p : PAY_METHODS) {
            leftover = leftover.replace(p, " ");
        }
        if (cats != null) {
            for (Category c : cats) {
                if (c.getCategoryName() != null) {
                    leftover = leftover.replace(c.getCategoryName(), " ");
                }
            }
        }
        if (members != null) {
            for (Member m : members) {
                if (m.getMemberName() != null) {
                    leftover = leftover.replace(m.getMemberName(), " ");
                }
            }
        }
        leftover = leftover.replaceAll("[的了吗呢啊吧嘛和与及在到至~\\-_,.，。？?！!：:、\\s]+", " ").trim();
        if (leftover.length() < 2) {
            return null;
        }
        String[] parts = leftover.split("\\s+");
        return parts[0].length() >= 2 ? parts[0] : null;
    }

    private static Integer intOrNull(JsonNode q, String field) {
        JsonNode v = q.get(field);
        if (v == null || v.isNull() || v.isTextual() && v.asText().isBlank()) {
            return null;
        }
        if (v.isNumber() || v.isTextual()) {
            int n = v.isNumber() ? v.asInt() : Integer.parseInt(v.asText().trim());
            return n;
        }
        return null;
    }

    private static Long longOrNull(JsonNode q, String field) {
        JsonNode v = q.get(field);
        if (v == null || v.isNull() || v.isTextual() && v.asText().isBlank()) {
            return null;
        }
        try {
            return v.isNumber() ? v.asLong() : Long.parseLong(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimalOrNull(JsonNode q, String field) {
        JsonNode v = q.get(field);
        if (v == null || v.isNull() || v.isTextual() && v.asText().isBlank()) {
            return null;
        }
        try {
            return v.isNumber() ? v.decimalValue() : new BigDecimal(v.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate dateOrNull(JsonNode q, String field) {
        String s = text(q, field);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim().substring(0, Math.min(10, s.trim().length())),
                    DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    public static final class AskDraft {
        public final String answer;
        public final RecordQuery query;
        public final String categoryName;

        public AskDraft(String answer, RecordQuery query, String categoryName) {
            this.answer = answer;
            this.query = query;
            this.categoryName = categoryName;
        }
    }
}

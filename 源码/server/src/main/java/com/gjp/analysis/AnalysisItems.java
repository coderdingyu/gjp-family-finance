package com.gjp.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjp.dify.DifyClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分析结论的排序与智能体结果合并。
 * 哪条 A* 规则命中、用哪个 level，一律以 Java 本地规则为准；
 * 智能体可润色 A* 的 title / basis / suggestion，并追加 S0/S1/S2 总判断。
 */
public final class AnalysisItems {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String RULE_TABLE = ""
            + "规则表：\n"
            + "A0–A8 由 Java 判定阈值。必须原样返回 snapshot.fired 里每一条 A* 的 code/level，数字必须来自 snapshot，不要改 A* 的 code/level，不要删 A*。\n"
            + "A0 空区间：收入与支出均为 0 → info\n"
            + "A1 异常月份：最高月支出 ≥ 其余有数据完整月均 × 1.30 → danger\n"
            + "A2 环比：最近两个完整有数据月份支出涨跌幅 ≥ 20% → warning（涨）/ good（跌）\n"
            + "A3 结余率：（收入−支出）/收入；无收入 warning；结余<0 danger；结余率<10% warning；否则 good\n"
            + "A4 预算：使用率>100% danger；≥80% 且完整月 warning\n"
            + "A5 分类集中：最高一级分类占总支出 ≥ 50% warning，否则 info\n"
            + "A6 商家集中：最高商家占已填商家消费 ≥ 20% warning，否则 info；无商家 info\n"
            + "A7 片区：课纲要求的可选维度，填写不规范时结论参考即可；最高片区 ≥ 40% 时文案可提集中，level 仍为 info。不要把片区当主叙事。\n"
            + "A8 人情往来：占总支出 ≥ 10% warning，否则 info\n"
            + "额外条目（可新增，数字必须来自 snapshot，禁止编造，禁止提及其他家庭）：\n"
            + "S0 总判断：fired 不含 A0 时必出。一段话把命中规则串成家庭诊断（钱漏在哪、偶发还是结构问题）。\n"
            + "S1 钱主要漏在哪：可选。点名 snapshot 里的具体分类/商家，给可执行建议。\n"
            + "S2 下月该盯什么：可选。1–2 条具体下一步。\n"
            + "S* 可用 level：danger / warning / info。\n";

    private AnalysisItems() {
    }

    public static int weight(String level) {
        switch (level == null ? "" : level) {
            case "danger":
                return 0;
            case "warning":
                return 1;
            case "info":
                return 2;
            default:
                return 3;
        }
    }

    public static boolean isExtraCode(String code) {
        return code != null && code.matches("S\\d+");
    }

    public static void sort(List<AnalysisItem> items) {
        items.sort((a, b) -> {
            boolean aS0 = "S0".equals(a.getCode());
            boolean bS0 = "S0".equals(b.getCode());
            if (aS0 != bS0) {
                return aS0 ? -1 : 1;
            }
            int w = weight(a.getLevel()) - weight(b.getLevel());
            if (w != 0) {
                return w;
            }
            String ca = a.getCode() == null ? "" : a.getCode();
            String cb = b.getCode() == null ? "" : b.getCode();
            return ca.compareTo(cb);
        });
    }

    /**
     * 用智能体文案覆盖本地 A* 结论，并追加 S0/S1/S2。
     * A* 的 code/level 以本地为准；解析失败时抛异常，由调用方回退全文案。
     */
    public static List<AnalysisItem> merge(List<AnalysisItem> local, String raw) throws Exception {
        if (local == null || local.isEmpty()) {
            return local;
        }
        JsonNode node = MAPPER.readTree(DifyClient.extractJson(raw));
        JsonNode arr = node.get("items");
        if (arr == null && node.isArray()) {
            arr = node;
        }
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            throw new IllegalArgumentException("智能体未返回 items");
        }
        Map<String, JsonNode> byCode = new LinkedHashMap<>();
        for (JsonNode it : arr) {
            String code = text(it, "code");
            if (code != null && !code.isBlank()) {
                byCode.put(code, it);
            }
        }
        if (byCode.isEmpty()) {
            throw new IllegalArgumentException("智能体 items 没有 code");
        }
        Set<String> localCodes = new HashSet<>();
        List<AnalysisItem> out = new ArrayList<>();
        for (AnalysisItem loc : local) {
            localCodes.add(loc.getCode());
            JsonNode n = byCode.get(loc.getCode());
            if (n == null) {
                out.add(loc);
                continue;
            }
            String title = firstNonBlank(text(n, "title"), loc.getTitle());
            String basis = firstNonBlank(text(n, "basis"), loc.getBasis());
            String suggestion = firstNonBlank(text(n, "suggestion"), loc.getSuggestion());
            out.add(new AnalysisItem(loc.getCode(), loc.getLevel(), title, basis, suggestion));
        }
        Set<String> seenExtra = new HashSet<>();
        for (JsonNode it : arr) {
            String code = text(it, "code");
            if (!isExtraCode(code) || localCodes.contains(code) || !seenExtra.add(code)) {
                continue;
            }
            String title = text(it, "title");
            if (title == null || title.isBlank()) {
                continue;
            }
            String level = extraLevel(text(it, "level"));
            String basis = firstNonBlank(text(it, "basis"), "");
            String suggestion = firstNonBlank(text(it, "suggestion"), "");
            out.add(new AnalysisItem(code, level, title, basis, suggestion));
        }
        return out;
    }

    private static String extraLevel(String level) {
        if ("danger".equals(level) || "warning".equals(level) || "info".equals(level) || "good".equals(level)) {
            return level;
        }
        return "info";
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}

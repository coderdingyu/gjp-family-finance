package com.gjp.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisItemsTest {

    @Test
    void sortDangerFirst() {
        List<AnalysisItem> items = new ArrayList<>();
        items.add(new AnalysisItem("A3", "good", "g", "b", "s"));
        items.add(new AnalysisItem("A1", "danger", "d", "b", "s"));
        items.add(new AnalysisItem("A5", "info", "i", "b", "s"));
        items.add(new AnalysisItem("A2", "warning", "w", "b", "s"));
        AnalysisItems.sort(items);
        assertEquals("A1", items.get(0).getCode());
        assertEquals("A2", items.get(1).getCode());
        assertEquals("A5", items.get(2).getCode());
        assertEquals("A3", items.get(3).getCode());
    }

    @Test
    void sortS0FirstThenLevels() {
        List<AnalysisItem> items = new ArrayList<>();
        items.add(new AnalysisItem("A3", "good", "g", "b", "s"));
        items.add(new AnalysisItem("S1", "info", "i", "b", "s"));
        items.add(new AnalysisItem("A1", "danger", "d", "b", "s"));
        items.add(new AnalysisItem("S0", "info", "总", "b", "s"));
        items.add(new AnalysisItem("A2", "warning", "w", "b", "s"));
        AnalysisItems.sort(items);
        assertEquals("S0", items.get(0).getCode());
        assertEquals("A1", items.get(1).getCode());
        assertEquals("A2", items.get(2).getCode());
        assertEquals("S1", items.get(3).getCode());
        assertEquals("A3", items.get(4).getCode());
    }

    @Test
    void mergeKeepsLocalLevelAndFillsWording() throws Exception {
        List<AnalysisItem> local = List.of(
                new AnalysisItem("A0", "info", "本地标题", "本地依据", "本地建议"));
        String raw = "{\"items\":[{\"code\":\"A0\",\"level\":\"danger\",\"title\":\"润色标题\",\"basis\":\"润色依据\",\"suggestion\":\"润色建议\"}]}";
        List<AnalysisItem> merged = AnalysisItems.merge(local, raw);
        assertEquals(1, merged.size());
        assertEquals("A0", merged.get(0).getCode());
        assertEquals("info", merged.get(0).getLevel());
        assertEquals("润色标题", merged.get(0).getTitle());
        assertEquals("润色依据", merged.get(0).getBasis());
        assertEquals("润色建议", merged.get(0).getSuggestion());
    }

    @Test
    void mergeIgnoresUnknownCodesAndKeepsMissingLocal() throws Exception {
        List<AnalysisItem> local = List.of(
                new AnalysisItem("A0", "info", "空", "无数据", "去记账"),
                new AnalysisItem("A3", "warning", "结余低", "10%", "省一点"));
        String raw = "{\"items\":[{\"code\":\"A9\",\"title\":\"幻觉\"},{\"code\":\"A0\",\"title\":\"空区间\"}]}";
        List<AnalysisItem> merged = AnalysisItems.merge(local, raw);
        assertEquals(2, merged.size());
        assertEquals("空区间", merged.get(0).getTitle());
        assertEquals("结余低", merged.get(1).getTitle());
    }

    @Test
    void mergeAppendsSItemsButCannotChangeACodeOrLevel() throws Exception {
        List<AnalysisItem> local = List.of(
                new AnalysisItem("A3", "warning", "结余低", "10%", "省"));
        String raw = "{\"items\":["
                + "{\"code\":\"A3\",\"level\":\"danger\",\"title\":\"润色\",\"basis\":\"b\",\"suggestion\":\"s\"},"
                + "{\"code\":\"S0\",\"level\":\"warning\",\"title\":\"总判断\",\"basis\":\"连起来\",\"suggestion\":\"看下月\"},"
                + "{\"code\":\"S1\",\"level\":\"info\",\"title\":\"漏在餐饮\",\"basis\":\"海底捞\",\"suggestion\":\"少点外卖\"},"
                + "{\"code\":\"A9\",\"title\":\"幻觉\"}"
                + "]}";
        List<AnalysisItem> merged = AnalysisItems.merge(local, raw);
        AnalysisItems.sort(merged);
        assertEquals("S0", merged.get(0).getCode());
        assertEquals("warning", merged.get(0).getLevel());
        assertEquals("总判断", merged.get(0).getTitle());
        AnalysisItem a3 = merged.stream().filter(i -> "A3".equals(i.getCode())).findFirst().orElseThrow();
        assertEquals("warning", a3.getLevel());
        assertEquals("润色", a3.getTitle());
        assertTrue(merged.stream().anyMatch(i -> "S1".equals(i.getCode())));
        assertTrue(merged.stream().noneMatch(i -> "A9".equals(i.getCode())));
        assertEquals(3, merged.size());
    }

    @Test
    void badJsonThrows() {
        List<AnalysisItem> local = List.of(new AnalysisItem("A0", "info", "t", "b", "s"));
        assertThrows(Exception.class, () -> AnalysisItems.merge(local, "not-json"));
        assertThrows(Exception.class, () -> AnalysisItems.merge(local, "{\"items\":[]}"));
        assertThrows(Exception.class, () -> AnalysisItems.merge(local, "{}"));
    }

    @Test
    void markdownFenceStillParses() throws Exception {
        List<AnalysisItem> local = List.of(new AnalysisItem("A0", "info", "t", "b", "s"));
        String raw = "```json\n{\"items\":[{\"code\":\"A0\",\"title\":\"ok\"}]}\n```";
        List<AnalysisItem> merged = AnalysisItems.merge(local, raw);
        assertEquals("ok", merged.get(0).getTitle());
        assertTrue(merged.get(0).getBasis().equals("b"));
    }
}

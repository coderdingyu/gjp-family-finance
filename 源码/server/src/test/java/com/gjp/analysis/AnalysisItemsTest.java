package com.gjp.analysis;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisItemsTest {
    private AnalysisItem budget(String id, String name) {
        AnalysisItem item = new AnalysisItem("A4", "danger", name, name + " 支出 100 元", "核对预算");
        item.setFindingId(id); item.getMetrics().put("amount", 100); return item;
    }
    @Test void preservesEveryLocalFactAndUsesUniqueBudgetEvidence() throws Exception {
        AnalysisItem a = budget("A4:1:2026-08", "甲"), b = budget("A4:2:2026-08", "乙");
        List<AnalysisItem> result = AnalysisItems.merge(List.of(a,b), """
            {"insights":[{"code":"S0","evidenceIds":["A4:2:2026-08"],"focus":"BUDGET"}]}
            """);
        assertSame(a, result.get(0)); assertSame(b, result.get(1));
        assertEquals("甲 支出 100 元", a.getBasis()); assertEquals("乙 支出 100 元", b.getBasis());
        assertEquals(List.of("A4:2:2026-08"), result.get(2).getEvidenceIds());
        assertTrue(result.get(2).getBasis().contains("乙")); assertFalse(result.get(2).getBasis().contains("甲"));
        assertEquals("info", result.get(2).getLevel());
    }
    @Test void rejectsUntrustedTextUnknownIdsAndUnsupportedStrategies() {
        List<AnalysisItem> local = List.of(budget("a", "甲"));
        for (String raw : List.of(
                "{\"items\":[{\"code\":\"A4\",\"basis\":\"999999 元\"}]}",
                "{\"insights\":[{\"code\":\"S0\",\"evidenceIds\":[\"other-family\"],\"focus\":\"BUDGET\"}]}",
                "{\"insights\":[{\"code\":\"S0\",\"evidenceIds\":[\"a\"],\"focus\":\"CHANGE\"}]}",
                "{\"insights\":[{\"code\":\"S99\",\"evidenceIds\":[\"a\"],\"focus\":\"BUDGET\"}]}",
                "{\"insights\":[{\"code\":\"S1\",\"evidenceIds\":[\"a\"],\"focus\":\"BUDGET\"}]}",
                "{\"insights\":[{\"code\":\"S0\",\"evidenceIds\":[\"a\"],\"focus\":\"BUDGET\",\"title\":\"伪造金额\"}]}",
                "{\"insights\":[]}", "not json")) {
            assertThrows(Exception.class, () -> AnalysisItems.merge(local, raw), raw);
        }
    }
    @Test void duplicateFindingIdsFailClosed() {
        assertThrows(Exception.class, () -> AnalysisItems.merge(List.of(budget("a", "甲"),budget("a", "乙")), "{}"));
    }
    @Test void markdownAndSortingStillWork() throws Exception {
        List<AnalysisItem> merged = AnalysisItems.merge(List.of(budget("a", "甲")), "```json\n{\"insights\":[{\"code\":\"S0\",\"evidenceIds\":[\"a\"],\"focus\":\"BUDGET\"}]}\n```");
        AnalysisItems.sort(merged); assertEquals("S0", merged.get(0).getCode());
        assertEquals("a", merged.get(1).getFindingId());
    }
}

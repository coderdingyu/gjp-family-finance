package com.gjp.asset.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Che300CatalogTest {

    @Test
    void pickDomesticModel3() {
        String json = "{\"code\":2000,\"msg\":\"ok\",\"data\":{\"list\":["
                + "{\"series_id\":\"34054\",\"series_name\":\"Model 3(进口)\",\"series_group_name\":\"进口特斯拉\"},"
                + "{\"series_id\":\"35621\",\"series_name\":\"Model 3\",\"series_group_name\":\"特斯拉中国\"}"
                + "]}}";
        assertEquals("35621", Che300Catalog.pickSeriesId(json, "Model 3"));
    }

    @Test
    void medianUsesLatestYearWhenRequestedYearMissing() {
        String json = "{\"code\":2000,\"msg\":\"ok\",\"data\":{\"list\":["
                + "{\"model_price\":\"23.55\",\"model_year\":\"2025\"},"
                + "{\"model_price\":\"25.95\",\"model_year\":\"2025\"},"
                + "{\"model_price\":\"28.55\",\"model_year\":\"2025\"},"
                + "{\"model_price\":\"33.95\",\"model_year\":\"2025\"},"
                + "{\"model_price\":\"23.19\",\"model_year\":\"2024\"}"
                + "]}}";
        BigDecimal med = Che300Catalog.medianGuideYuan(json, 2026);
        assertNotNull(med);
        // 23.55/25.95/28.55/33.95 万 → 中位 27.25 万
        assertEquals(0, new BigDecimal("272500.00").compareTo(med));
    }

    @Test
    void teslaBrandSeeded() {
        assertEquals(Integer.valueOf(120), Che300Catalog.brandId("特斯拉"));
    }

    @Test
    void fromGuideKeepsEstimate() {
        var listing = ResidualAge.fromGuidePrice(
                new BigDecimal("235500"), 2026, null, null,
                java.time.LocalDate.of(2026, 9, 3));
        assertNotNull(listing.estimate);
        assertTrue(listing.note.contains("车300"));
        assertEquals("指导价估算", listing.source);
    }
}

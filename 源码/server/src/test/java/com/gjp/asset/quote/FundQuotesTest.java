package com.gjp.asset.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FundQuotesTest {

    static final String JSONP = "jsonpgz({\"fundcode\":\"110022\",\"name\":\"易方达消费行业股票\",\"jzrq\":\"2026-09-02\",\"dwjz\":\"2.9260\",\"gsz\":\"2.9301\",\"gszzl\":\"0.14\",\"gztime\":\"2026-09-03 14:00\"});";

    @Test
    void parseJsonpUsesGsz() {
        QuoteSnapshot q = FundQuotes.parseJsonp(JSONP);
        assertNotNull(q);
        assertEquals("易方达消费行业股票", q.getName());
        assertEquals(0, new BigDecimal("2.9301").compareTo(q.getLastPrice()));
        assertEquals("2026-09-03 14:00", q.getQuoteTime());
        assertEquals("tiantian", q.getSource());
    }

    @Test
    void html404IsNull() {
        assertNull(FundQuotes.parseJsonp("<!doctype html><title>页面未找到 - 东方财富网</title>"));
        assertNull(FundQuotes.parseJsonp(null));
    }
}

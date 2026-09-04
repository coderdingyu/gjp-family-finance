package com.gjp.asset.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositInterestTest {

    private static final BigDecimal P = new BigDecimal("100000");
    private static final BigDecimal RATE = new BigDecimal("2.5");
    private static final LocalDate START = LocalDate.of(2026, 3, 3);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    @Test
    void simpleSixMonthsAbout1250() {
        DepositInterest.Result r = DepositInterest.compute(P, RATE, 12, "simple", START, TODAY);
        assertNotNull(r);
        // 2026-03-03 → 2026-09-03 = 184 days; 100000 * 2.5% * 184/365 ≈ 1260.27
        assertEquals(184, r.daysHeld);
        assertEquals(new BigDecimal("1260.27"), r.profit);
        assertEquals(new BigDecimal("101260.27"), r.valueNow);
        assertTrue(r.remainDays > 170 && r.remainDays < 190);
        assertEquals("单利", r.interestMethodLabel);
    }

    @Test
    void yearCompoundUsesYearFraction() {
        DepositInterest.Result r = DepositInterest.compute(P, RATE, 12, "compound_year", START, TODAY);
        assertNotNull(r);
        double years = 184 / 365.0;
        double expect = 100000 * (Math.pow(1.025, years) - 1);
        assertEquals(expect, r.profit.doubleValue(), 0.02);
        assertEquals("年复利(利滚利)", r.interestMethodLabel);
    }

    @Test
    void monthCompoundUsesDaysOver30() {
        DepositInterest.Result r = DepositInterest.compute(P, RATE, 12, "compound_month", START, TODAY);
        assertNotNull(r);
        double months = 184 / 30.0;
        double expect = 100000 * (Math.pow(1.0 + 0.025 / 12.0, months) - 1);
        assertEquals(expect, r.profit.doubleValue(), 0.02);
        assertEquals("月复利", r.interestMethodLabel);
    }

    @Test
    void afterMaturityCapsDaysAndRemainZero() {
        LocalDate start = LocalDate.of(2025, 8, 1);
        LocalDate today = LocalDate.of(2026, 9, 3);
        DepositInterest.Result r = DepositInterest.compute(P, RATE, 12, "simple", start, today);
        assertNotNull(r);
        assertEquals(0, r.remainDays);
        assertEquals("已可支取", r.remainLabel);
        // term end 2026-08-01; daysHeld = 365 (2025-08-01 + 12 months)
        assertEquals(365, r.daysHeld);
        assertEquals(new BigDecimal("2500.00"), r.profit);
    }

    @Test
    void missingRateOrMethodSkips() {
        assertNull(DepositInterest.compute(P, null, 12, "simple", START, TODAY));
        assertNull(DepositInterest.compute(P, RATE, 12, null, START, TODAY));
        assertNull(DepositInterest.compute(P, RATE, 12, "bogus", START, TODAY));
        assertNull(DepositInterest.compute(P, RATE, 12, "simple", null, TODAY));
    }
}

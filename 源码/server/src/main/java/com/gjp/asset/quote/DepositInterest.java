package com.gjp.asset.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Deposit / 银行理财 interest as of a given calendar day (Asia/Shanghai caller supplies today).
 *
 * Formulas (annual_rate is percent, e.g. 2.35 means 2.35%):
 * <ul>
 *   <li>simple 单利: profit = principal * (rate/100) * daysHeld / 365</li>
 *   <li>compound_year 年复利(利滚利): profit = principal * ((1+rate/100)^yearsElapsed - 1)
 *       where yearsElapsed = daysHeld / 365.0 (actual year fraction)</li>
 *   <li>compound_month 月复利: profit = principal * ((1+rate/100/12)^monthsElapsed - 1)
 *       where monthsElapsed = daysHeld / 30.0</li>
 * </ul>
 * daysHeld is capped at the term (start.plusMonths(termMonths)); no interest after maturity.
 * remainDays = max(0, termEnd - today); 0 → 已可支取.
 */
public final class DepositInterest {

    public static final String SIMPLE = "simple";
    public static final String COMPOUND_YEAR = "compound_year";
    public static final String COMPOUND_MONTH = "compound_month";

    private DepositInterest() {
    }

    public static Result compute(BigDecimal principal, BigDecimal annualRatePct,
                                 Integer termMonths, String method,
                                 LocalDate start, LocalDate today) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) < 0
                || annualRatePct == null || method == null || method.isBlank()
                || start == null || today == null) {
            return null;
        }
        String m = method.trim();
        if (!SIMPLE.equals(m) && !COMPOUND_YEAR.equals(m) && !COMPOUND_MONTH.equals(m)) {
            return null;
        }
        LocalDate asOf = today;
        LocalDate termEnd = null;
        if (termMonths != null && termMonths > 0) {
            termEnd = start.plusMonths(termMonths);
            if (asOf.isAfter(termEnd)) {
                asOf = termEnd;
            }
        }
        long daysHeld = ChronoUnit.DAYS.between(start, asOf);
        if (daysHeld < 0) {
            daysHeld = 0;
        }
        double rate = annualRatePct.doubleValue() / 100.0;
        double profitRaw;
        if (SIMPLE.equals(m)) {
            profitRaw = principal.doubleValue() * rate * (daysHeld / 365.0);
        } else if (COMPOUND_YEAR.equals(m)) {
            // yearsElapsed = daysHeld / 365
            double years = daysHeld / 365.0;
            profitRaw = principal.doubleValue() * (Math.pow(1.0 + rate, years) - 1.0);
        } else {
            // monthsElapsed = daysHeld / 30
            double months = daysHeld / 30.0;
            profitRaw = principal.doubleValue() * (Math.pow(1.0 + rate / 12.0, months) - 1.0);
        }
        if (profitRaw < 0) {
            profitRaw = 0;
        }
        BigDecimal profit = BigDecimal.valueOf(profitRaw).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valueNow = principal.add(profit).setScale(2, RoundingMode.HALF_UP);
        int remainDays = 0;
        String remainLabel = null;
        if (termEnd != null) {
            long remain = ChronoUnit.DAYS.between(today, termEnd);
            remainDays = (int) Math.max(remain, 0);
            remainLabel = remainDays == 0 ? "已可支取" : ("还有 " + remainDays + " 天到期");
        }
        return new Result(profit, valueNow, (int) daysHeld, remainDays, remainLabel, methodLabel(m), m);
    }

    public static String methodLabel(String method) {
        if (SIMPLE.equals(method)) {
            return "单利";
        }
        if (COMPOUND_YEAR.equals(method)) {
            return "年复利(利滚利)";
        }
        if (COMPOUND_MONTH.equals(method)) {
            return "月复利";
        }
        return method;
    }

    public static final class Result {
        public final BigDecimal profit;
        public final BigDecimal valueNow;
        public final int daysHeld;
        public final int remainDays;
        public final String remainLabel;
        public final String interestMethodLabel;
        public final String method;

        Result(BigDecimal profit, BigDecimal valueNow, int daysHeld, int remainDays,
               String remainLabel, String interestMethodLabel, String method) {
            this.profit = profit;
            this.valueNow = valueNow;
            this.daysHeld = daysHeld;
            this.remainDays = remainDays;
            this.remainLabel = remainLabel;
            this.interestMethodLabel = interestMethodLabel;
            this.method = method;
        }
    }
}

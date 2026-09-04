package com.gjp.asset;

import com.gjp.entity.Loan;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 有起始还款日时，已还期数按今天自动推进（每天读接口即更新，不改库）。
 */
public final class LoanProgress {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private LoanProgress() {
    }

    public static int paidMonths(Loan loan) {
        return paidMonths(loan, LocalDate.now(SHANGHAI));
    }

    public static int paidMonths(Loan loan, LocalDate today) {
        int total = loan.getTotalMonths() == null ? 0 : Math.max(loan.getTotalMonths(), 0);
        if (loan.getStartDate() != null) {
            long m = ChronoUnit.MONTHS.between(loan.getStartDate(), today);
            if (m < 0) {
                m = 0;
            }
            if (m > total) {
                m = total;
            }
            return (int) m;
        }
        int paid = loan.getPaidMonths() == null ? 0 : loan.getPaidMonths();
        if (paid < 0) {
            paid = 0;
        }
        return Math.min(paid, total);
    }

    public static boolean auto(Loan loan) {
        return loan.getStartDate() != null;
    }
}

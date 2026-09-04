package com.gjp.asset;

import com.gjp.entity.Loan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanProgressTest {

    @Test
    void usesStartDateNotStoredPaid() {
        Loan loan = new Loan();
        loan.setStartDate(LocalDate.of(2021, 9, 8));
        loan.setTotalMonths(240);
        loan.setPaidMonths(3);
        int paid = LoanProgress.paidMonths(loan, LocalDate.of(2026, 9, 3));
        assertEquals(59, paid);
        assertTrue(LoanProgress.auto(loan));
    }

    @Test
    void sameMonthIsZero() {
        Loan loan = new Loan();
        loan.setStartDate(LocalDate.of(2026, 9, 8));
        loan.setTotalMonths(12);
        assertEquals(0, LoanProgress.paidMonths(loan, LocalDate.of(2026, 9, 3)));
    }

    @Test
    void capsAtTotal() {
        Loan loan = new Loan();
        loan.setStartDate(LocalDate.of(2000, 1, 1));
        loan.setTotalMonths(12);
        assertEquals(12, LoanProgress.paidMonths(loan, LocalDate.of(2026, 9, 3)));
    }

    @Test
    void noStartDateUsesStored() {
        Loan loan = new Loan();
        loan.setPaidMonths(7);
        loan.setTotalMonths(24);
        assertEquals(7, LoanProgress.paidMonths(loan, LocalDate.of(2026, 9, 3)));
    }
}

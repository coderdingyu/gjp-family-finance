package com.gjp.asset.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidualAgeTest {

    @Test
    void tesla2021FromCost() {
        var listing = ResidualAge.estimateCar(
                new BigDecimal("168000"), 2021, LocalDate.of(2021, 9, 8), null,
                LocalDate.of(2026, 9, 3));
        assertNotNull(listing.estimate);
        assertTrue(listing.estimate.compareTo(new BigDecimal("70000")) > 0);
        assertTrue(listing.estimate.compareTo(new BigDecimal("120000")) < 0);
        assertTrue(listing.note.contains("车龄"));
        assertNull(listing.reason);
    }

    @Test
    void noCostFailsSoft() {
        var listing = ResidualAge.estimateCar(null, 2021, null, null, LocalDate.of(2026, 9, 3));
        assertNull(listing.estimate);
        assertNotNull(listing.reason);
    }
}

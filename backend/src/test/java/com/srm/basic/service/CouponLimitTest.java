package com.srm.basic.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouponLimitTest {
    private final LocalDate day = LocalDate.of(2026, 9, 23);

    @Test
    void respectsWindowAndRemainingUses() {
        assertTrue(CouponLimit.usable(1, LocalDate.of(2026, 9, 23), LocalDate.of(2026, 12, 31), day, 2, 1));
        assertFalse(CouponLimit.usable(1, LocalDate.of(2026, 9, 24), LocalDate.of(2026, 12, 31), day, 2, 0));
        assertFalse(CouponLimit.usable(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 22), day, 2, 0));
        assertFalse(CouponLimit.usable(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), day, 2, 2));
        assertFalse(CouponLimit.usable(0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), day, 2, 0));
        assertTrue(CouponLimit.usable(1, null, null, day, null, 0));
    }
}

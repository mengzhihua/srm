package com.srm.basic.service;

import com.srm.basic.entity.PriceList;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShelfPriceTest {
    @Test
    void bandsComeFromTheContract() {
        assertNull(ShelfPrice.afterBands(null, new BigDecimal("100"), Collections.<ShelfPrice.Band>emptyList()));
        assertEquals(0, ShelfPrice.afterBands(new BigDecimal("45"), new BigDecimal("10"), Collections.<ShelfPrice.Band>emptyList())
                .compareTo(new BigDecimal("45.0000")));
        java.util.List<ShelfPrice.Band> bands = Arrays.asList(
                new ShelfPrice.Band(new BigDecimal("10"), new BigDecimal("0.95")),
                new ShelfPrice.Band(new BigDecimal("100"), new BigDecimal("0.90")));
        assertEquals(0, ShelfPrice.afterBands(new BigDecimal("45"), new BigDecimal("9"), bands).compareTo(new BigDecimal("45.0000")));
        assertEquals(0, ShelfPrice.afterBands(new BigDecimal("45"), new BigDecimal("10"), bands).compareTo(new BigDecimal("42.7500")));
        assertEquals(0, ShelfPrice.afterBands(new BigDecimal("45"), new BigDecimal("100"), bands).compareTo(new BigDecimal("40.5000")));
    }

    @Test
    void minQtyHidesTheOnePiecePrice() {
        PriceList row = new PriceList();
        row.setSupplierCode("SUP01");
        row.setMaterialCode("SKU001");
        row.setPrice(new BigDecimal("45"));
        row.setMinQty(new BigDecimal("10"));
        row.setValidFrom(LocalDate.of(2025, 1, 1));
        row.setValidTo(LocalDate.of(2026, 12, 31));
        row.setStatus(1);
        LocalDate day = LocalDate.of(2026, 9, 23);
        java.util.List<ShelfPrice.Band> bands = Collections.singletonList(new ShelfPrice.Band(new BigDecimal("10"), new BigDecimal("0.95")));
        assertNull(ShelfPrice.afterBands(AgreementPrice.pick(Collections.singletonList(row), "SUP01", "SKU001", BigDecimal.ONE, day), BigDecimal.ONE, bands));
        assertEquals(0, ShelfPrice.afterBands(AgreementPrice.pick(Collections.singletonList(row), "SUP01", "SKU001", new BigDecimal("10"), day), new BigDecimal("10"), bands)
                .compareTo(new BigDecimal("42.7500")));
    }

    @Test
    void couponPercentOffRejectsOverOneHundred() {
        BigDecimal price = new BigDecimal("42.7500");
        assertEquals(0, ShelfPrice.afterCoupon(price, null).compareTo(price));
        assertEquals(0, ShelfPrice.afterCoupon(price, BigDecimal.ZERO).compareTo(price));
        assertEquals(0, ShelfPrice.afterCoupon(price, new BigDecimal("10")).compareTo(new BigDecimal("38.4750")));
        assertEquals(0, ShelfPrice.afterCoupon(price, new BigDecimal("100")).compareTo(new BigDecimal("0.0000")));
        assertNull(ShelfPrice.afterCoupon(null, new BigDecimal("10")));
        assertThrows(IllegalArgumentException.class, () -> ShelfPrice.afterCoupon(price, new BigDecimal("101")));
    }
}

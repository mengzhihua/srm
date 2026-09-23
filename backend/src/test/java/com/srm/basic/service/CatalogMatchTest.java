package com.srm.basic.service;

import com.srm.basic.entity.Material;
import com.srm.basic.entity.PriceList;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatalogMatchTest {
    @Test
    void matchesCodeThenManufacturerPartThenBrandAndPart() {
        Material bolt = material("M-1", "螺栓", "世达", "SATA-123");
        Material glove = material("M-2", "手套", "安丹达", "AND-9");
        assertEquals("M-1", CatalogMatch.match(Arrays.asList(bolt, glove), " m-1 ").getCode());
        assertEquals("M-2", CatalogMatch.match(Arrays.asList(bolt, glove), "and-9").getCode());
        assertEquals("M-1", CatalogMatch.match(Arrays.asList(bolt, glove), "世达 SATA-123 十只").getCode());
        assertNull(CatalogMatch.match(Arrays.asList(bolt, glove), "随便写的扳手"));
    }

    @Test
    void agreementPriceSkipsExpiredAndShortQuantity() {
        PriceList expired = price("SUP01", "M-1", "8", "2020-01-01", "2020-12-31", "1");
        PriceList later = price("SUP01", "M-1", "12", "2026-01-01", "2026-12-31", "10");
        PriceList cheaper = price("SUP01", "M-1", "11", "2026-01-01", null, "1");
        LocalDate day = LocalDate.of(2026, 6, 1);
        assertEquals(0, AgreementPrice.pick(Arrays.asList(expired, later, cheaper), "sup01", "m-1", new BigDecimal("2"), day)
                .compareTo(new BigDecimal("11")));
        assertEquals(0, AgreementPrice.pick(Arrays.asList(expired, later), "SUP01", "M-1", new BigDecimal("10"), day)
                .compareTo(new BigDecimal("12")));
        assertNull(AgreementPrice.pick(Collections.singletonList(later), "SUP01", "M-1", new BigDecimal("9"), day));
    }

    private static Material material(String code, String name, String brand, String part) {
        Material material = new Material();
        material.setCode(code);
        material.setName(name);
        material.setBrand(brand);
        material.setMfrPartNo(part);
        return material;
    }

    private static PriceList price(String supplier, String material, String amount, String from, String to, String min) {
        PriceList row = new PriceList();
        row.setSupplierCode(supplier);
        row.setMaterialCode(material);
        row.setPrice(new BigDecimal(amount));
        row.setValidFrom(LocalDate.parse(from));
        row.setValidTo(to == null ? null : LocalDate.parse(to));
        row.setMinQty(new BigDecimal(min));
        row.setStatus(1);
        return row;
    }
}

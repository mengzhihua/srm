package com.srm.basic.service;

import com.srm.basic.entity.PriceList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 从协议价里挑一条：供应商和物料相符、当天有效、数量达到起订量。多条时取生效日更晚的，同一天取更低价。 */
public final class AgreementPrice {
    private AgreementPrice() {}

    public static BigDecimal pick(List<PriceList> lists, String supplier, String material, BigDecimal qty, LocalDate day) {
        PriceList best = choose(lists, supplier, material, qty, day);
        return best == null ? null : best.getPrice();
    }

    public static PriceList choose(List<PriceList> lists, String supplier, String material, BigDecimal qty, LocalDate day) {
        return select(lists, supplier, material, qty, day, true);
    }

    /** 当天仍有效的协议，不看起订量。用来列出这份协议上的折扣档。 */
    public static PriceList contract(List<PriceList> lists, String supplier, String material, LocalDate day) {
        return select(lists, supplier, material, null, day, false);
    }

    private static PriceList select(List<PriceList> lists, String supplier, String material, BigDecimal qty, LocalDate day, boolean checkMin) {
        if (lists == null || blank(supplier) || blank(material) || day == null || (checkMin && qty == null)) {
            return null;
        }
        PriceList best = null;
        for (PriceList row : lists) {
            if (!usable(row, supplier, material, qty, day, checkMin)) {
                continue;
            }
            if (best == null || newer(row, best)) {
                best = row;
            }
        }
        return best;
    }

    private static boolean usable(PriceList row, String supplier, String material, BigDecimal qty, LocalDate day, boolean checkMin) {
        if (row.getStatus() != null && row.getStatus() != 1) {
            return false;
        }
        if (row.getPrice() == null || row.getPrice().signum() < 0) {
            return false;
        }
        if (!supplier.equalsIgnoreCase(text(row.getSupplierCode())) || !material.equalsIgnoreCase(text(row.getMaterialCode()))) {
            return false;
        }
        if (row.getValidFrom() != null && day.isBefore(row.getValidFrom())) {
            return false;
        }
        if (row.getValidTo() != null && day.isAfter(row.getValidTo())) {
            return false;
        }
        if (!checkMin) {
            return true;
        }
        return row.getMinQty() == null || qty.compareTo(row.getMinQty()) >= 0;
    }

    private static boolean newer(PriceList candidate, PriceList current) {
        LocalDate left = candidate.getValidFrom();
        LocalDate right = current.getValidFrom();
        if (left == null && right != null) {
            return false;
        }
        if (left != null && right == null) {
            return true;
        }
        if (left != null && !left.equals(right)) {
            return left.isAfter(right);
        }
        return candidate.getPrice().compareTo(current.getPrice()) < 0;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}

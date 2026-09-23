package com.srm.basic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 选品价。数量折扣打在协议价上：满 10 件九五折，满 100 件九折。没有协议价就不编造牌价。 */
public final class ShelfPrice {
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal RATE_10 = new BigDecimal("0.95");
    private static final BigDecimal RATE_100 = new BigDecimal("0.90");

    private ShelfPrice() {}

    public static BigDecimal afterQty(BigDecimal agreement, BigDecimal qty) {
        if (agreement == null) {
            return null;
        }
        BigDecimal rate = BigDecimal.ONE;
        if (qty != null && qty.compareTo(HUNDRED) >= 0) {
            rate = RATE_100;
        } else if (qty != null && qty.compareTo(TEN) >= 0) {
            rate = RATE_10;
        }
        return agreement.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal afterCoupon(BigDecimal price, BigDecimal percent) {
        if (percent != null && percent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("优惠券折扣不能超过 100");
        }
        if (price == null || percent == null || percent.signum() <= 0) {
            return price;
        }
        BigDecimal keep = BigDecimal.ONE.subtract(percent.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        return price.multiply(keep).setScale(4, RoundingMode.HALF_UP);
    }
}

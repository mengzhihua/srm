package com.srm.basic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 选品价。折扣取协议上达到的数量档，没有档就用协议价。没有协议价就不编造牌价。 */
public final class ShelfPrice {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ShelfPrice() {}

    public static final class Band {
        private final BigDecimal minQty;
        private final BigDecimal rate;

        public Band(BigDecimal minQty, BigDecimal rate) {
            this.minQty = minQty;
            this.rate = rate;
        }

        public BigDecimal getMinQty() {
            return minQty;
        }

        public BigDecimal getRate() {
            return rate;
        }
    }

    public static BigDecimal afterBands(BigDecimal agreement, BigDecimal qty, List<Band> bands) {
        if (agreement == null) {
            return null;
        }
        BigDecimal rate = BigDecimal.ONE;
        if (qty != null && bands != null) {
            Band best = null;
            for (Band band : bands) {
                if (band == null || band.minQty == null || band.rate == null) {
                    continue;
                }
                if (band.rate.signum() <= 0 || band.rate.compareTo(BigDecimal.ONE) > 0) {
                    continue;
                }
                if (qty.compareTo(band.minQty) < 0) {
                    continue;
                }
                if (best == null || band.minQty.compareTo(best.minQty) > 0) {
                    best = band;
                }
            }
            if (best != null) {
                rate = best.rate;
            }
        }
        return agreement.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /** 已经填写的正单价保持不动。空单价先按协议折扣档，再乘上当时记下的优惠券。没有协议价就不编造。 */
    public static BigDecimal forOrder(BigDecimal explicit, BigDecimal agreement, BigDecimal qty, List<Band> bands, BigDecimal couponPercent) {
        if (explicit != null && explicit.signum() > 0) {
            return explicit;
        }
        BigDecimal priced = afterBands(agreement, qty, bands);
        if (priced == null || couponPercent == null || couponPercent.signum() <= 0) {
            return priced;
        }
        return afterCoupon(priced, couponPercent);
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

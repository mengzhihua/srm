package com.srm.basic.service;

import java.time.LocalDate;

/** 优惠券能不能用：状态有效、当天在生效期内、已用次数还没到上限。 */
public final class CouponLimit {
    private CouponLimit() {}

    public static boolean usable(Integer status, LocalDate validFrom, LocalDate validTo, LocalDate day,
                                 Integer maxUses, Integer usedCount) {
        if (status != null && status != 1) {
            return false;
        }
        if (day == null) {
            return false;
        }
        if (validFrom != null && day.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && day.isAfter(validTo)) {
            return false;
        }
        if (maxUses != null && maxUses <= 0) {
            return false;
        }
        int used = usedCount == null ? 0 : usedCount;
        return maxUses == null || used < maxUses;
    }
}

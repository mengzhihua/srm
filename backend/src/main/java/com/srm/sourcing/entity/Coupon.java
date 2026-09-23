package com.srm.sourcing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 采购员自己保存的优惠券。要有生效期和可用次数，加入需求清单时扣一次。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_coupon")
public class Coupon extends BaseEntity {
    private String ownerName;
    private String code;
    private BigDecimal percent;
    private Integer status;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Integer maxUses;
    private Integer usedCount;
}

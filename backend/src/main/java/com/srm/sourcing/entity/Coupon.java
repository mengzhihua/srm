package com.srm.sourcing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 采购员自己保存的优惠券。只有编码和折扣百分比，这一截没有有效期和次数。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_coupon")
public class Coupon extends BaseEntity {
    private String ownerName;
    private String code;
    private BigDecimal percent;
    private Integer status;
}

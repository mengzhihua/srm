package com.srm.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 协议上的数量折扣。rate 是应付比例，0.95 表示九五折。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_discount_band")
public class DiscountBand extends BaseEntity {
    private Long priceListId;
    private BigDecimal minQty;
    private BigDecimal rate;
}

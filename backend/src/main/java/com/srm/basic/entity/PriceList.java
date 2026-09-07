package com.srm.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 价格/合同价 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_price_list")
public class PriceList extends BaseEntity {
    private String supplierCode;
    private String materialCode;
    private java.math.BigDecimal price;
    private String currency;
    private java.math.BigDecimal minQty;
    private java.time.LocalDate validFrom;
    private java.time.LocalDate validTo;
    private String contractNo;
    private Integer status;
}

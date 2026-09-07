package com.srm.sourcing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 供应商报价 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_rfq_quote")
public class RfqQuote extends BaseEntity {
    private Long rfqId;
    private String supplierCode;
    /** SUBMITTED/AWARDED/LOST */
    private String status;
    private BigDecimal totalAmount;
    private String remark;

    @TableField(exist = false)
    private List<RfqQuoteLine> lines;
}

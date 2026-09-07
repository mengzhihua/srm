package com.srm.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 采购订单 PO */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_purchase_order")
public class PurchaseOrder extends BaseEntity {
    private String code;
    private String supplierCode;
    private String plantCode;
    private String currency;
    private BigDecimal totalAmount;
    /** DRAFT/APPROVED/SENT/CONFIRMED/PARTIALLY_RECEIVED/RECEIVED/CLOSED/CANCELLED */
    private String status;
    private String sapPoNo;
    private LocalDateTime sapSentAt;
    private LocalDateTime confirmedAt;
    private LocalDate expectedDate;
    /** MANUAL/PR/RFQ */
    private String sourceType;
    private String sourceCode;
    private String remark;

    @TableField(exist = false)
    private List<PoLine> lines;
}

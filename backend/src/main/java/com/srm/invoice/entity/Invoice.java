package com.srm.invoice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 发票 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_invoice")
public class Invoice extends BaseEntity {
    private String code;
    private String supplierCode;
    private String poCode;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    /** SUBMITTED/MATCHED/MISMATCH/APPROVED/POSTED/REJECTED */
    private String status;
    private String sapInvoiceDoc;
    private String remark;

    @TableField(exist = false)
    private List<InvoiceLine> lines;
}

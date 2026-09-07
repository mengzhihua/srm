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

/** 发票行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_invoice_line")
public class InvoiceLine extends BaseEntity {
    private Long invoiceId;
    private Long grLineId;
    private Long poLineId;
    private String materialCode;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
}

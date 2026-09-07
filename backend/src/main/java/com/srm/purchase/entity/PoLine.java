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

/** 采购订单行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_po_line")
public class PoLine extends BaseEntity {
    private Long poId;
    private Integer lineNo;
    private String materialCode;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    private LocalDate deliveryDate;
    private BigDecimal shippedQty;
    private BigDecimal receivedQty;
    private BigDecimal rejectedQty;
    private BigDecimal invoicedQty;
    private String sapItemNo;
}

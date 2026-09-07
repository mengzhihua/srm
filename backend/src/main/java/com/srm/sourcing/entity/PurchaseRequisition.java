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

/** 采购申请 PR */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_purchase_requisition")
public class PurchaseRequisition extends BaseEntity {
    private String code;
    private String plantCode;
    private String requester;
    private String department;
    /** DRAFT/SUBMITTED/APPROVED/REJECTED/ORDERED/CANCELLED */
    private String status;
    private String remark;

    @TableField(exist = false)
    private List<PrLine> lines;
}

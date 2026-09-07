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

/** 询价单 RFQ */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_rfq")
public class Rfq extends BaseEntity {
    private String code;
    private String title;
    private String plantCode;
    /** DRAFT/PUBLISHED/QUOTING/AWARDED/CANCELLED */
    private String status;
    private LocalDateTime deadline;
    /** 受邀供应商编码，逗号分隔 */
    private String supplierCodes;
    private String remark;

    @TableField(exist = false)
    private List<RfqLine> lines;
    @TableField(exist = false)
    private List<RfqQuote> quotes;
}

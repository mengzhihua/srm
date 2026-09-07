package com.srm.delivery.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 发货通知 ASN */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_asn")
public class Asn extends BaseEntity {
    private String code;
    private Long poId;
    private String poCode;
    private String supplierCode;
    private String plantCode;
    /** CREATED/SYNCED/RECEIVING/RECEIVED/POSTED/CANCELLED/SYNC_FAILED */
    private String status;
    private LocalDate expectedDate;
    private String carrier;
    private String trackingNo;
    private Long wmsAsnId;
    private String wmsAsnCode;
    private LocalDateTime syncedAt;
    private LocalDateTime receivedAt;
    private String remark;
    private BigDecimal totalQty;
    private BigDecimal receivedQty;
    private BigDecimal rejectedQty;

    @TableField(exist = false)
    private List<AsnLine> lines;
}

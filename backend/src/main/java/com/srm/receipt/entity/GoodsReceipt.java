package com.srm.receipt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 收货单 GR */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_goods_receipt")
public class GoodsReceipt extends BaseEntity {
    private String code;
    private Long asnId;
    private String asnCode;
    private Long poId;
    private String poCode;
    private String supplierCode;
    private String plantCode;
    /** PENDING/POSTED/POST_FAILED */
    private String status;
    private LocalDateTime receivedAt;
    private String sapMaterialDoc;
    private LocalDateTime sapPostedAt;
    private BigDecimal totalReceivedQty;
    private BigDecimal totalRejectedQty;
    /** WMS_CALLBACK/WMS_POLL/MANUAL */
    private String source;

    @TableField(exist = false)
    private List<GrLine> lines;
}

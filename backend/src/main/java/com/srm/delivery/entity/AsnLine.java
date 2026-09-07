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

/** ASN 行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_asn_line")
public class AsnLine extends BaseEntity {
    private Long asnId;
    private Integer lineNo;
    private Long poLineId;
    private String materialCode;
    private BigDecimal qty;
    private String lotNo;
    private LocalDate expiryDate;
    private BigDecimal receivedQty;
    private BigDecimal rejectedQty;
}

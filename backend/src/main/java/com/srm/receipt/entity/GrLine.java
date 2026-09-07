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

/** 收货行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_gr_line")
public class GrLine extends BaseEntity {
    private Long grId;
    private Long asnLineId;
    private Long poLineId;
    private String materialCode;
    private BigDecimal receivedQty;
    private BigDecimal rejectedQty;
    private BigDecimal acceptedQty;
    private String lotNo;
    private BigDecimal amount;
}

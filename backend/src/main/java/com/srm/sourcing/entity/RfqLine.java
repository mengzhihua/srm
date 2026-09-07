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

/** RFQ 行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_rfq_line")
public class RfqLine extends BaseEntity {
    private Long rfqId;
    private Integer lineNo;
    private String materialCode;
    private BigDecimal qty;
    private LocalDate requiredDate;
}

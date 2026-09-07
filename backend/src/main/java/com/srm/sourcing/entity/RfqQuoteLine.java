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

/** 报价行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_rfq_quote_line")
public class RfqQuoteLine extends BaseEntity {
    private Long quoteId;
    private Long rfqLineId;
    private String materialCode;
    private BigDecimal price;
    private Integer leadTimeDays;
}

package com.srm.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 考核记录（每张收货单一条） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_eval_record")
public class SupplierEvaluationRecord extends BaseEntity {
    private String supplierCode;
    private String grCode;
    private String asnCode;
    private String poCode;
    private Boolean onTime;
    /** 收货准确率 = accepted/shipped 百分数 */
    private BigDecimal qtyAccuracy;
    /** 质量合格率 = 1 - rejected/received */
    private BigDecimal qualityRate;
    private Integer leadDays;
    private String wmsRemark;
    private BigDecimal wmsScore;
    private BigDecimal score;
    private String period;
}

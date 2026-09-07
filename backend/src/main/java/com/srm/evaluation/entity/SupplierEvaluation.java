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

/** 考核汇总（供应商+期间） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_eval_summary")
public class SupplierEvaluation extends BaseEntity {
    private String supplierCode;
    private String period;
    private Integer receiptCount;
    private BigDecimal onTimeRate;
    private BigDecimal qtyAccuracy;
    private BigDecimal qualityRate;
    private BigDecimal avgScore;
    private String grade;
    private Boolean sapSynced;
    private LocalDateTime sapSyncedAt;
}

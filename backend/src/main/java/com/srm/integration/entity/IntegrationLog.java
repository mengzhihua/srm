package com.srm.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 集成日志 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_integration_log")
public class IntegrationLog extends BaseEntity {
    /** OUT=本系统发出 / IN=外部推送 */
    private String direction;
    /** SAP/WMS */
    private String system;
    private String action;
    private String bizType;
    private Long bizId;
    private String bizCode;
    private String request;
    private String response;
    /** SUCCESS/FAILED */
    private String status;
    private String errorMsg;
    private Integer durationMs;
    private Integer retryCount;
}

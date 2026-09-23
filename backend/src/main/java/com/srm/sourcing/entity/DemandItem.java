package com.srm.sourcing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 需求清单。先记下要买的文字，匹配上物料后才能转采购申请。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_demand_item")
public class DemandItem extends BaseEntity {
    private String ownerName;
    private String requestText;
    private String materialCode;
    private BigDecimal qty;
    private String status;
    private Long prId;
}

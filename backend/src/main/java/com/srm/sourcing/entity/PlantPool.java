package com.srm.sourcing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工厂允许购买的物料。不在池里的物料，选了这个工厂就不再出现。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_plant_pool")
public class PlantPool extends BaseEntity {
    private String plantCode;
    private String materialCode;
    private Integer status;
}

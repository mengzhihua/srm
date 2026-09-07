package com.srm.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工厂/收货地 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_plant")
public class Plant extends BaseEntity {
    private String code;
    private String name;
    private String sapPlantCode;
    private String sapStorageLocation;
    private String wmsWarehouseCode;
    private String wmsOwnerCode;
    private String address;
    private Integer status;
}

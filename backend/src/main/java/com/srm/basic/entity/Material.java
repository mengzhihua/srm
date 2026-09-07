package com.srm.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 物料 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_material")
public class Material extends BaseEntity {
    private String code;
    private String name;
    private String spec;
    private String unit;
    private String category;
    private String sapMaterialCode;
    private String wmsItemCode;
    private java.math.BigDecimal taxRate;
    private Integer status;
}

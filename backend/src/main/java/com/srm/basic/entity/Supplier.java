package com.srm.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier")
public class Supplier extends BaseEntity {
    private String code;
    private String name;
    private String shortName;
    private String taxNo;
    private String contact;
    private String phone;
    private String email;
    private String address;
    private String bankName;
    private String bankAccount;
    private String category;
    private String paymentTerms;
    private String currency;
    private String sapVendorCode;
    private String wmsSupplierCode;
    /** 考核回写等级 A/B/C/D */
    private String grade;
    /** 最近考核得分 */
    private java.math.BigDecimal score;
    /** 1 启用 / 0 停用 / 2 黑名单 */
    private Integer status;
}

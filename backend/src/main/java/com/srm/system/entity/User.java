package com.srm.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户。role: ADMIN(全部) / BUYER(采购员, 不含用户维护) / SUPPLIER(供应商门户, 限本人 supplierCode 数据) / VIEWER(只读)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_user")
public class User extends BaseEntity {
    public static final String ADMIN = "ADMIN";
    public static final String BUYER = "BUYER";
    public static final String SUPPLIER = "SUPPLIER";
    public static final String VIEWER = "VIEWER";

    private String username;
    /** 写入时可携带明文密码，响应中永不输出 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String realName;
    private String role;
    /** SUPPLIER 角色绑定的供应商编码（srm_supplier.code） */
    private String supplierCode;
    private Integer status;
    private LocalDateTime lastLoginAt;
}

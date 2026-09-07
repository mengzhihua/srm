package com.srm.system.auth;

import com.srm.system.entity.User;

/**
 * 角色访问策略（按 HTTP 方法 + 路径判断）：
 * <ul>
 *   <li>任何登录用户可读（GET）</li>
 *   <li>VIEWER 不可写</li>
 *   <li>BUYER 可写全部业务接口，不可维护用户(/api/system/**)与集成日志重试(/api/integration/**)</li>
 *   <li>SUPPLIER 供应商门户：仅可写寻源报价/采购订单确认/发货ASN/发票提交相关接口；
 *       数据范围由服务层按 supplierCode 过滤</li>
 *   <li>ADMIN 无限制</li>
 * </ul>
 * /api/auth/** 属于登录用户自助操作（改密、登出），所有角色均可。
 */
public final class AccessPolicy {
    private AccessPolicy() {
    }

    /** SUPPLIER 可写的路径前缀（服务层再做 supplierCode 数据校验） */
    private static final String[] SUPPLIER_WRITE_PREFIXES = {
            "/api/sourcing/rfq/",      // 报价提交
            "/api/purchase/order/",    // 订单确认
            "/api/delivery/asn",       // 创建ASN（仅POST根路径+指定动作在服务层校验）
            "/api/invoice"             // 发票提交
    };

    public static boolean allows(String role, String method, String path) {
        if (User.ADMIN.equals(role)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (User.BUYER.equals(role)) {
            return !path.startsWith("/api/system/") && !path.startsWith("/api/integration/");
        }
        if (User.SUPPLIER.equals(role)) {
            // 供应商只能做报价、确认订单、创建ASN、提交发票；拉取/重试/手工收货等后台动作禁止
            if (path.contains("/pull-wms") || path.contains("/manual-receipt")
                    || path.contains("/retry") || path.contains("/sync-sap")) {
                return false;
            }
            for (String p : SUPPLIER_WRITE_PREFIXES) {
                if (path.startsWith(p)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }
}

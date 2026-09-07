package com.srm.system.auth;

import com.srm.common.BizException;
import com.srm.system.entity.User;

/** 当前请求线程绑定的登录用户 */
public final class CurrentUser {
    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static User get() {
        return HOLDER.get();
    }

    static void set(User user) {
        HOLDER.set(user);
    }

    static void clear() {
        HOLDER.remove();
    }

    /** SUPPLIER 登录时返回其绑定的供应商编码，其它角色返回 null */
    public static String supplierCode() {
        User u = get();
        return u != null && User.SUPPLIER.equals(u.getRole()) ? u.getSupplierCode() : null;
    }

    /** SUPPLIER 访问他人数据时抛出 403 语义的业务异常 */
    public static void checkSupplier(String supplierCode) {
        String mine = supplierCode();
        if (mine != null && !mine.equals(supplierCode)) {
            throw new BizException("无权操作其他供应商的数据");
        }
    }

    /** 仅 ADMIN/BUYER 可执行的采购侧动作 */
    public static void requireBuyerSide() {
        User u = get();
        if (u != null && User.SUPPLIER.equals(u.getRole())) {
            throw new BizException("供应商账号无权执行此操作");
        }
    }
}

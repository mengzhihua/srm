package com.srm.sourcing.service;

/** 邮件审批只登记。不连接邮件网关，也不把采购申请改成已审批。 */
public final class MailApprovalNote {
    public static final String DETAIL = "已登记，未连接邮件网关";

    private MailApprovalNote() {}

    public static String address(String email) {
        if (email == null) {
            throw new IllegalArgumentException("请填写收件邮箱");
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1) {
            throw new IllegalArgumentException("请填写收件邮箱");
        }
        return trimmed;
    }
}

package com.srm.sourcing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购申请的邮件审批登记。记下收件人，不发送，也不改申请状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_mail_approval")
public class MailApproval extends BaseEntity {
    private Long prId;
    private String prCode;
    private String email;
    private String status;
    private String detail;
}

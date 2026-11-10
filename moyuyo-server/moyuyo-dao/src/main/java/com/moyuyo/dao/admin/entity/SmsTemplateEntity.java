package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信模板实体（对应 mo_sms_template 表）
 */
@Data
@TableName("mo_sms_template")
public class SmsTemplateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模板名称 */
    private String name;

    /** 模板类型：CODE/NOTICE/MARKETING */
    private String type;

    /** 服务商模板ID */
    private String templateId;

    /** 模板内容 */
    private String content;

    /** 审核状态：APPROVED/PENDING/REJECTED */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 是否启用：1=启用 0=禁用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

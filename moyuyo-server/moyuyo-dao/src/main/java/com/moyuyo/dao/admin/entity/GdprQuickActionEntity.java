package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * GDPR 快速操作执行流水实体
 */
@Data
@TableName("mo_gdpr_quick_action")
public class GdprQuickActionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作类型：EXPORT/DELETE/CONSENT_EXPORT/REVOKE/COMPLAINT */
    private String actionType;

    /** 目标用户ID */
    private Long targetUserId;

    /** 导出格式：JSON/CSV */
    private String format;

    /** 导出包下载地址 */
    private String downloadUrl;

    /** 账号删除宽限期截止时间 */
    private LocalDateTime gracePeriodEnd;

    /** 状态：PENDING/PROCESSING/COMPLETED/REVOKED/REJECTED */
    private String status;

    /** 操作管理员ID */
    private Long operatorId;

    /** 操作管理员 */
    private String operatorName;

    /** 备注 */
    private String note;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@Data
@TableName("mo_audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 操作动作 */
    private String action;

    /** 操作模块 */
    private String module;

    /** 资源ID */
    private String resourceId;

    /** 操作详情 */
    private String detail;

    /** IP地址 */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 结果：SUCCESS/FAILURE */
    private String result;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

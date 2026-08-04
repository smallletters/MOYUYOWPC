package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控告警配置实体（对应 mo_risk_alert_config 表）
 */
@Data
@TableName("mo_risk_alert_config")
public class RiskAlertConfigEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 告警名称 */
    private String alertName;

    /** 告警类型 */
    private String alertType;

    /** 关联规则ID */
    private Long ruleId;

    /** 告警阈值 */
    private Integer threshold;

    /** 监控指标 */
    private String metric;

    /**
     * 触发条件：GREATER_THAN/LESS_THAN/EQUAL
     * 注意：`condition` 是 MySQL/MariaDB 保留字，必须用反引号包裹
     */
    @TableField("`condition`")
    private String condition;

    /** 通知渠道（多个以逗号分隔） */
    private String notifyChannels;

    /** 通知用户（多个以逗号分隔） */
    private String notifyUsers;

    /**
     * 状态：1=启用 0=禁用
     * 注意：V20260727 迁移将字段从 VARCHAR 改为 TINYINT(1)
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

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

    /** 通知渠道（多个以逗号分隔） */
    private String notifyChannels;

    /** 通知用户（多个以逗号分隔） */
    private String notifyUsers;

    /** 状态：ENABLED/DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

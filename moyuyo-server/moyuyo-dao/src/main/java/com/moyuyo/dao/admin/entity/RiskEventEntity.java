package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 风控事件实体
 */
@Data
@TableName("mo_risk_event")
public class RiskEventEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联规则ID */
    private Long ruleId;

    /** 规则编码 */
    private String ruleCode;

    /** 用户ID */
    private Long userId;

    /** 事件类型：LOGIN/ORDER/PAYMENT */
    private String eventType;

    /** 业务ID */
    private String businessId;

    /** 风险等级：LOW/MEDIUM/HIGH/CRITICAL */
    private String riskLevel;

    /** 已执行动作：BLOCK/REVIEW/VERIFY */
    private String actionTaken;

    /** 详情JSON */
    private String detailJson;

    /** 状态：PENDING/REVIEWED/RESOLVED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

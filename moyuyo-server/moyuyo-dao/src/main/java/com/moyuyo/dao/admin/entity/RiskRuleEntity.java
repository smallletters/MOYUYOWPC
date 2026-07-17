package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 风控规则实体
 */
@Data
@TableName("mo_risk_rule")
public class RiskRuleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 规则编码（唯一） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型：LOGIN/ORDER/PAYMENT/COUPON */
    private String ruleType;

    /** 条件JSON */
    private String conditionJson;

    /** 执行动作：BLOCK/REVIEW/VERIFY/LOG */
    private String action;

    /** 优先级 */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 规则描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

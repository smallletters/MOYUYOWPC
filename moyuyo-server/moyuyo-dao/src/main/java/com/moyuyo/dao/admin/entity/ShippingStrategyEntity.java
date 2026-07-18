package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_shipping_strategy")
public class ShippingStrategyEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 策略名称 */
    private String name;

    /** 适用区域 */
    private String region;

    /** 配送方式 */
    private String method;

    /** 计费规则描述 */
    private String ruleDesc;

    /** 优先级 */
    private Integer priority;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

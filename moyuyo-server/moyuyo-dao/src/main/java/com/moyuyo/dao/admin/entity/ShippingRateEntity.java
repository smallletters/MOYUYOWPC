package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运费规则（mo_shipping_rate）。
 * 一条规则 = 一个 zone × 一个 method × 一个计费方式 × 一组价格参数。
 * 计算公式（按 charge_type）：
 *   1=按件  freight = first_charge + continue_charge * (CEIL(qty / continue_unit) - 1)
 *   2=按重  freight = first_charge + continue_charge * (CEIL(weight_g / continue_unit) - 1)
 *   3=按金额 freight = first_charge + continue_charge * (CEIL(amt / continue_unit) - 1)
 * 若 subtotal >= free_threshold，则 freight = 0（满额免邮，门槛基于满减前商品金额）。
 */
@Data
@TableName("mo_shipping_rate")
public class ShippingRateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联 mo_shipping_zone.id */
    private Long zoneId;

    /** 关联 mo_shipping_method.id */
    private Long methodId;

    /** 计费方式：1=按件 2=按重 3=按金额 */
    private Integer chargeType;

    /** 首费（USD） */
    private BigDecimal firstCharge;

    /** 首件/首重数量（默认 1） */
    private Integer firstUnit;

    /** 续费（USD） */
    private BigDecimal continueCharge;

    /** 续件/续重单位：按件时是件，按重时是 g */
    private Integer continueUnit;

    /** 满额包邮门槛（NULL=不包邮） */
    private BigDecimal freeThreshold;

    /** 货币（默认 USD） */
    private String currency;

    /** 优先级，越小越优先 */
    private Integer priority;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

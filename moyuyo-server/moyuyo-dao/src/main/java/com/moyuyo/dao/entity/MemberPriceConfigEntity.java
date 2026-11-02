package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员等级价格折扣配置
 *
 * <p>discountRate 为折扣系数：1.00 = 原价，0.95 = 9.5 折。
 */
@Data
@TableName("mo_member_price_config")
public class MemberPriceConfigEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会员等级：NORMAL / SILVER / GOLD / PLATINUM / DIAMOND */
    private String level;

    /** 折扣系数，取值范围 [0.50, 1.00] */
    private BigDecimal discountRate;

    /** 等级说明（后台展示用） */
    private String description;

    /** 是否启用 0否1是 */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

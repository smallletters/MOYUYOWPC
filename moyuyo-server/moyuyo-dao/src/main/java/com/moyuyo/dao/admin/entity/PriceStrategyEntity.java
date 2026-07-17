package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价格策略实体
 */
@Data
@TableName("mo_price_strategy")
public class PriceStrategyEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 策略名称 */
    private String strategyName;

    /** 策略类型：DISCOUNT/FLASH_SALE/MEMBER/GRADED */
    private String strategyType;

    /** 适用范围：PRODUCT/CATEGORY/BRAND */
    private String applyTo;

    /** 适用值（商品ID/类目ID/品牌ID） */
    private Long applyValue;

    /** 折扣类型：PERCENTAGE/FIXED_AMOUNT */
    private String discountType;

    /** 折扣值 */
    private BigDecimal discountValue;

    /** 最低金额 */
    private BigDecimal minAmount;

    /** 最大折扣 */
    private BigDecimal maxDiscount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 优先级 */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建人ID */
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

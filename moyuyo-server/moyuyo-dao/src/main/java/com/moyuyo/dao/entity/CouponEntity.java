package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_coupon")
public class CouponEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    private String type;

    private BigDecimal discountValue;

    private BigDecimal minOrderAmount;

    private BigDecimal maxDiscountAmount;

    private Integer totalCount;

    private Integer claimedCount;

    private Integer usedCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Boolean active;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 当前登录用户是否已领取（非持久化字段，由 Service 层在列表接口按 userId 填充）
     * 用于 C 端"领券中心"页面判断按钮"立即领取 / 已领取"展示
     */
    @TableField(exist = false)
    private Boolean claimedByMe;
}

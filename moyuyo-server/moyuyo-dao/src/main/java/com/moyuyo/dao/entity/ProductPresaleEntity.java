package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商品预售配置（1:1 商品）。
 * 独立表存储，避免与 mo_product.attributes JSON 混用，便于预售状态机与订单侧引用。
 */
@Data
@TableName("mo_product_presale")
public class ProductPresaleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID（唯一） */
    private Long productId;

    /** 预售类型：DISPLAY 展示型 / FULL 全款 / DEPOSIT 定金 */
    private String presaleType;

    /** 预售状态：DRAFT / SCHEDULED / ONGOING / SOLD_OUT / ENDED */
    private String status;

    /** 预售价格（不得高于商品售价） */
    private BigDecimal presalePrice;

    /** 预售开始时间 */
    private LocalDateTime startTime;

    /** 预售结束时间 */
    private LocalDateTime endTime;

    /** 预计发货日期 */
    private LocalDate shipDate;

    /** 预售库存上限（null 表示不限） */
    private Integer stockLimit;

    /** 已售预售数量 */
    private Integer soldCount;

    /** 每人限购数量（null 表示不限） */
    private Integer limitPerUser;

    /** 未付款自动取消分钟数（null 表示用系统默认） */
    private Integer autoCancelMinutes;

    /** 定金金额（DEPOSIT 必填） */
    private BigDecimal depositAmount;

    /** 定金抵扣金额（尾款 = 预售价 - 抵扣金额） */
    private BigDecimal depositOffset;

    /** 尾款支付开始时间 */
    private LocalDateTime balanceStart;

    /** 尾款支付结束时间 */
    private LocalDateTime balanceEnd;

    /** 定金是否可退 */
    private Boolean depositRefundable;

    /** 预售须知（APP 详情页展示） */
    private String noticeText;

    /** 延迟发货补偿：逾期天数阈值 */
    private Integer compensationDays;

    /** 延迟发货补偿：补偿比例（百分比） */
    private BigDecimal compensationPercent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单改价记录实体（对应 mo_order_price_modify 表）
 */
@Data
@TableName("mo_order_price_modify")
public class OrderPriceModifyEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 订单ID */
  private Long orderId;

  /** 订单号 */
  private String orderNo;

  /** 原始金额 */
  private BigDecimal originalAmount;

  /** 调整金额(正为加价,负为减价) */
  private BigDecimal adjustAmount;

  /** 最终金额 */
  private BigDecimal finalAmount;

  /** 改价原因 */
  private String reason;

  /** 改价类型: FREIGHT(补运费)/DISCOUNT(减差价)/MANUAL(人工优惠) */
  private String reasonType;

  /** 操作人 */
  private String operator;

  /** 状态: PENDING/APPROVED/REJECTED */
  private String status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}

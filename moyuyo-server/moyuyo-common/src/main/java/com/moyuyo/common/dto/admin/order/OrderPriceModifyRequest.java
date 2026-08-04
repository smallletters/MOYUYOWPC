package com.moyuyo.common.dto.admin.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台创建订单改价请求
 * 支持通过 orderId 或 orderNo 指定订单,二者至少填一个
 */
@Data
public class OrderPriceModifyRequest {

  /** 订单ID,与 orderNo 二选一 */
  private Long orderId;

  /** 订单号,与 orderId 二选一 */
  private String orderNo;

  /** 原始金额,可选(用于审计) */
  private BigDecimal originalAmount;

  /** 调整金额,必填(正数为加价,负数为减价) */
  @NotNull(message = "调整金额不能为空")
  private BigDecimal adjustAmount;

  /** 改价原因 */
  private String reason;

  /** 改价原因类型:MANUAL(人工)/PROMOTION(促销)/SYSTEM(系统),空则默认 MANUAL */
  private String reasonType;

  /** 操作人,空则默认"系统" */
  private String operator;
}

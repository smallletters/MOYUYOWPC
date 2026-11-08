package com.moyuyo.common.dto.admin.order;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台订单退款请求（对齐 WooCommerce 订单详情页的退款弹窗）
 * <p>
 * mode=AUTO  ：自动调用原支付渠道退款 API
 * mode=MANUAL：管理员已在渠道后台线下退款，仅录入第三方退款流水号
 */
@Data
public class OrderRefundRequest {

  /** 退款金额（不传则默认退剩余可退金额） */
  private BigDecimal amount;

  /** 退款原因 */
  private String reason;

  /** 退款备注 */
  private String description;

  /** 退款方式：AUTO（自动原路退回）/ MANUAL（手动录入流水号），默认 AUTO */
  private String mode;

  /** 第三方退款流水号（mode=MANUAL 时必填） */
  private String transactionId;
}

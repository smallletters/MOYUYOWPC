package com.moyuyo.common.dto.prime;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Prime 订阅请求体。
 * <p>
 * dev/mock 环境：直接落库为 ACTIVE 状态并按 plan 写入 expireAt。
 * prod 环境：应通过 Stripe/PayPal webhook 异步确认后再激活。
 */
public class PrimeSubscribeRequest {

  @NotBlank(message = "套餐编码不能为空")
  private String planCode;

  /** 支付渠道：STRIPE / PAYPAL / WECHAT / ALIPAY */
  private String payChannel = "STRIPE";

  public String getPlanCode() { return planCode; }
  public void setPlanCode(String planCode) { this.planCode = planCode; }
  public String getPayChannel() { return payChannel; }
  public void setPayChannel(String payChannel) { this.payChannel = payChannel; }
}
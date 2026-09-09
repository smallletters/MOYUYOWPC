package com.moyuyo.common.dto.prime;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Prime 订阅请求体。
 * <p>
 * - 密钥未配置（模拟）：直接落库为 ACTIVE 并按 plan 写入 expireAt；
 * - 已配置真实支付：创建 Stripe Checkout Session，由 webhook 异步确认后激活。
 */
@Data
public class PrimeSubscribeRequest {

  @NotBlank(message = "套餐编码不能为空")
  private String planCode;

  /** 支付渠道：当前支持 STRIPE */
  private String payChannel = "STRIPE";

  /** 客户端类型：H5 / APP（APP 端 successUrl/cancelUrl 使用自定义 scheme） */
  private String clientType = "H5";

  /** APP 端自定义 scheme 回跳地址，如 moyuyo://pay/return */
  private String schemeBase;

  /** 支付成功/取消回跳基础地址（H5 传前端 origin），用于拼回 Prime 页刷新状态 */
  private String returnUrl;
}

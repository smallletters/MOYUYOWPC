package com.moyuyo.common.dto.prime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prime 订阅响应。
 * <p>
 * - simulated=true 表示支付密钥未配置、走模拟直开（dev/联调），此时返回 status；
 * - simulated=false 表示已创建真实 Stripe Checkout Session，前端用 sessionUrl 跳转支付，
 *   支付结果由 Stripe webhook 异步激活（用户回到本页刷新状态即可）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Prime 订阅响应")
public class PrimeSubscribeVO {

  /** 是否为模拟直开（dev/联调：未配置真实支付密钥时直接激活） */
  @Schema(description = "是否为模拟直开", example = "true")
  private boolean simulated;

  /** simulated=true 时返回开通后的最新订阅状态 */
  @Schema(description = "模拟直开后的订阅状态")
  private PrimeStatusVO status;

  /** 真实支付通道的 Stripe Checkout Session 支付页地址 */
  @Schema(description = "Stripe Checkout 支付页 URL", example = "https://checkout.stripe.com/c/pay/cs_test_xxx")
  private String sessionUrl;

  /** Stripe Checkout Session id（后续对账/取消用） */
  @Schema(description = "Stripe 支付会话 id", example = "cs_test_xxx")
  private String paymentId;

  /** 支付渠道：STRIPE */
  @Schema(description = "支付渠道", example = "STRIPE")
  private String payChannel;

  /** 开通套餐编码：MONTHLY / YEARLY */
  @Schema(description = "套餐编码", example = "YEARLY")
  private String planCode;

  /** 套餐名称 */
  @Schema(description = "套餐名称", example = "年付会员")
  private String planName;
}

package com.moyuyo.common.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "支付响应")
public class CreatePaymentResponse {

    @Schema(description = "支付会话ID(Stripe PaymentIntent / Checkout Session / PayPal Order)",
            example = "cs_test_a1b2c3d4e5f6g7h8i9j0")
    private String paymentId;

    @Schema(description = "Stripe 客户端密钥(备用)", example = "pi_3MqF9T2eZvKYlo2C0XjZf8xR_secret_xyz")
    private String clientSecret;

    @Schema(description = "Stripe Checkout Session URL(H1 修复：前端 web-view 直接打开)",
            example = "https://checkout.stripe.com/c/pay/cs_test_a1b2c3d4e5f6g7h8i9j0")
    private String sessionUrl;

    @Schema(description = "Stripe Publishable Key(前端 Stripe.js / Apple Pay SDK 用)")
    private String publishableKey;

    @Schema(description = "PayPal 支付页面URL", example = "https://www.paypal.com/checkout?token=EC-123456")
    private String approvalUrl;

    @Schema(description = "支付渠道: STRIPE / PAYPAL", example = "STRIPE")
    private String payChannel;
}

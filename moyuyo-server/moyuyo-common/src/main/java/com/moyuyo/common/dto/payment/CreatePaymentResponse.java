package com.moyuyo.common.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
// 保留全参构造：兼容 PaymentServiceImpl 中旧的 new CreatePaymentResponse(...) 七参调用
@AllArgsConstructor
// 新增无参 + Builder：方便只填部分原生通道专属字段（如 paypalClientId、applePayMerchantId 等）
@NoArgsConstructor
@SuperBuilder
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

    /* ========== 原生 APP 支付通道专属字段（APP 端打包 iOS/Android 时使用，H5/小程序忽略） ========== */

    @Schema(description = "PayPal App ClientID（uni.requestPayment provider=paypal 时 orderInfo.clientId 填此值）。"
            + " 空串/H5 场景不返回，避免信息泄露。",
            example = "Axxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx_y")
    private String paypalClientId;

    @Schema(description = "PayPal 运行环境：sandbox / live（与 payment.paypal.mode 一致）", example = "sandbox")
    private String paypalEnvironment;

    @Schema(description = "Apple Pay Merchant ID（需与 iOS entitlements & Stripe Dashboard 绑定一致）。"
            + " 非 iOS 或未配置时返回空串。", example = "merchant.com.moyuyo.app")
    private String applePayMerchantId;

    @Schema(description = "Apple Pay / Google Pay 币种代码（大写 ISO 4217）", example = "USD")
    private String currencyCode;

    @Schema(description = "Apple Pay / Google Pay 国家代码（大写 ISO 3166-1 alpha-2）", example = "US")
    private String countryCode;
}

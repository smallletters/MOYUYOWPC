package com.moyuyo.common.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发起支付请求")
public class CreatePaymentRequest {

    @NotBlank(message = "订单号不能为空")
    @Schema(description = "订单号", example = "ORD2026071500000001")
    private String orderNo;

    @NotBlank(message = "支付渠道不能为空")
    @Schema(description = "支付渠道: STRIPE / PAYPAL", example = "STRIPE")
    private String payChannel;

    @Schema(description = "支付方式细分（同一渠道下区分钱包APP/Apple Pay/支付宝等）: APPLE_PAY / GOOGLE_PAY / ALIPAY / CASH_APP / VENMO / PAYPAL / LINK / AFFIRM / AFTERPAY", example = "GOOGLE_PAY")
    private String payMethod;

    @Schema(description = "客户端类型: H5 / APP / MP。APP 端 successUrl 使用自定义 scheme（如 moyuyo://pay/return）", example = "H5")
    private String clientType;

    @Schema(description = "客户端注册的自定义 scheme 回跳地址（仅 clientType=APP 时生效），示例 moyuyo://pay/return", example = "moyuyo://pay/return")
    private String schemeBase;

    @Schema(description = "成功后的跳转URL", example = "https://moyuyo.com/order/success")
    private String returnUrl;
}

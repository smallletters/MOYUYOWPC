package com.moyuyo.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 第三方退款渠道配置（service 模块副本）
 * <p>
 * 历史说明：原文件位于 moyuyo-api 模块，service 模块未声明对 api 的依赖，
 * 编译期出现"找不到符号"。为遵守模块边界（service 不反向依赖 api），
 * 此处提供与原文件完全等价的副本。原 moyuyo-api 模块下的同名类保留以兼容
 * 可能的反射 / 包扫描路径。
 */
@Configuration("serviceRefundChannelConfig")
public class RefundChannelConfig {

    // ===== Stripe =====
    @Value("${payment.stripe.secret-key:}")
    private String stripeSecretKey;

    // ===== PayPal =====
    @Value("${payment.paypal.client-id:}")
    private String paypalClientId;
    @Value("${payment.paypal.client-secret:}")
    private String paypalClientSecret;
    @Value("${payment.paypal.mode:sandbox}")
    private String paypalMode;

    // ===== 微信支付 =====
    @Value("${payment.wechat.app-id:}")
    private String wechatAppId;
    @Value("${payment.wechat.mch-id:}")
    private String wechatMchId;
    @Value("${payment.wechat.api-key:}")
    private String wechatApiKey;
    @Value("${payment.wechat.cert-path:}")
    private String wechatCertPath;
    @Value("${payment.wechat.notify-url:}")
    private String wechatNotifyUrl;

    // ===== 支付宝 =====
    @Value("${payment.alipay.app-id:}")
    private String alipayAppId;
    @Value("${payment.alipay.private-key:}")
    private String alipayPrivateKey;
    @Value("${payment.alipay.public-key:}")
    private String alipayPublicKey;
    @Value("${payment.alipay.gateway:https://openapi.alipay.com/gateway.do}")
    private String alipayGateway;
    @Value("${payment.alipay.notify-url:}")
    private String alipayNotifyUrl;

    // ===== 银联 =====
    @Value("${payment.unionpay.merchant-id:}")
    private String unionpayMerchantId;
    @Value("${payment.unionpay.cert-path:}")
    private String unionpayCertPath;
    @Value("${payment.unionpay.cert-password:}")
    private String unionpayCertPassword;
    @Value("${payment.unionpay.gateway:https://api.unionpayintl.com/4.0/gateway}")
    private String unionpayGateway;
    @Value("${payment.unionpay.notify-url:}")
    private String unionpayNotifyUrl;

    // ===== Getters =====

    public String getStripeSecretKey() { return stripeSecretKey; }
    public String getPaypalClientId() { return paypalClientId; }
    public String getPaypalClientSecret() { return paypalClientSecret; }
    public String getPaypalMode() { return paypalMode; }

    public String getWechatAppId() { return wechatAppId; }
    public String getWechatMchId() { return wechatMchId; }
    public String getWechatApiKey() { return wechatApiKey; }
    public String getWechatCertPath() { return wechatCertPath; }
    public String getWechatNotifyUrl() { return wechatNotifyUrl; }

    public String getAlipayAppId() { return alipayAppId; }
    public String getAlipayPrivateKey() { return alipayPrivateKey; }
    public String getAlipayPublicKey() { return alipayPublicKey; }
    public String getAlipayGateway() { return alipayGateway; }
    public String getAlipayNotifyUrl() { return alipayNotifyUrl; }

    public String getUnionpayMerchantId() { return unionpayMerchantId; }
    public String getUnionpayCertPath() { return unionpayCertPath; }
    public String getUnionpayCertPassword() { return unionpayCertPassword; }
    public String getUnionpayGateway() { return unionpayGateway; }
    public String getUnionpayNotifyUrl() { return unionpayNotifyUrl; }

    public boolean isStripeEnabled() {
        return stripeSecretKey != null && !stripeSecretKey.isEmpty()
                && !stripeSecretKey.contains("placeholder");
    }
    public boolean isPaypalEnabled() {
        return paypalClientId != null && !paypalClientId.isEmpty()
                && !paypalClientId.contains("placeholder");
    }
    public boolean isWechatEnabled() {
        return wechatMchId != null && !wechatMchId.isEmpty();
    }
    public boolean isAlipayEnabled() {
        return alipayAppId != null && !alipayAppId.isEmpty();
    }
    public boolean isUnionpayEnabled() {
        return unionpayMerchantId != null && !unionpayMerchantId.isEmpty();
    }
}
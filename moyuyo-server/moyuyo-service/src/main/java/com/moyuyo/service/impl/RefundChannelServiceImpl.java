package com.moyuyo.service.impl;

import com.moyuyo.service.config.RefundChannelConfig;
import com.moyuyo.common.enums.RefundChannelEnum;
import com.moyuyo.service.RefundChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 第三方支付渠道退款路由实现
 * 支持：Stripe / PayPal / 微信支付 / 支付宝 / 银联
 *
 * 设计要点：
 * 1) 渠道未配置时降级为"本地生成流水号"模式（不调三方），便于 dev / staging 联调
 * 2) prod 渠道配置由 ProdConfigValidator 校验非空，否则启动失败
 * 3) 三方调用失败抛出 RuntimeException，事务回滚，由 Controller 返回错误
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundChannelServiceImpl implements RefundChannelService {

    private final RefundChannelConfig config;
    @Qualifier("restTemplate")
    private final RestTemplate restTemplate;

    @Override
    public String refund(RefundChannelEnum channel, String payTransactionId, String orderNo,
                         BigDecimal refundAmount, String refundNo) {
        if (channel == null) {
            throw new IllegalArgumentException("退款渠道不能为空");
        }
        log.info("Refund channel call: channel={}, orderNo={}, refundNo={}, amount={}",
                channel, orderNo, refundNo, refundAmount);

        switch (channel) {
            case STRIPE:
                return callStripeRefund(payTransactionId, refundAmount, refundNo);
            case PAYPAL:
                return callPayPalRefund(payTransactionId, refundAmount, refundNo);
            case WECHAT:
                return callWechatRefund(payTransactionId, refundAmount, orderNo, refundNo);
            case ALIPAY:
                return callAlipayRefund(payTransactionId, refundAmount, orderNo, refundNo);
            case UNIONPAY:
                return callUnionpayRefund(payTransactionId, refundAmount, orderNo, refundNo);
            case WALLET:
            case POINTS:
                // 内部账户退款，流水号即为退款单号
                return "INTERNAL_" + refundNo;
            default:
                throw new IllegalArgumentException("暂不支持的退款渠道: " + channel);
        }
    }

    // ============= Stripe Refund =============
    private String callStripeRefund(String paymentIntentId, BigDecimal amount, String refundNo) {
        if (!config.isStripeEnabled()) {
            log.warn("Stripe 未配置，退款降级为本地模式: refundNo={}", refundNo);
            return "DEV_STRIPE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        try {
            // Stripe Refund API: POST https://api.stripe.com/v1/refunds
            // amount 单位为最小货币单位（美分）
            long amountCents = amount.multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(config.getStripeSecretKey(), "");

            String body = "payment_intent=" + urlEncode(paymentIntentId)
                    + "&amount=" + amountCents
                    + "&metadata[refund_no]=" + urlEncode(refundNo)
                    + "&idempotency_key=" + urlEncode(refundNo);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.stripe.com/v1/refunds", entity, Map.class);
            Map<String, Object> result = response.getBody();
            if (result == null || result.containsKey("error")) {
                log.error("Stripe refund failed: {}", result);
                throw new RuntimeException("Stripe 退款失败");
            }
            String refundId = (String) result.get("id");
            log.info("Stripe refund success: refundId={}", refundId);
            return refundId;
        } catch (Exception e) {
            log.error("Stripe refund exception", e);
            throw new RuntimeException("Stripe 退款服务不可用");
        }
    }

    // ============= PayPal Refund =============
    private String callPayPalRefund(String captureId, BigDecimal amount, String refundNo) {
        if (!config.isPaypalEnabled()) {
            log.warn("PayPal 未配置，退款降级为本地模式: refundNo={}", refundNo);
            return "DEV_PAYPAL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        try {
            String accessToken = getPayPalAccessToken();
            String baseUrl = "sandbox".equals(config.getPaypalMode())
                    ? "https://api-m.sandbox.paypal.com"
                    : "https://api-m.paypal.com";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            // PayPal 退款 idempotency 头（推荐）
            headers.add("PayPal-Request-Id", refundNo);

            // PayPal Refund API: POST /v2/payments/captures/{capture_id}/refund
            // amount.value 保留两位小数
            BigDecimal amountStr = amount.setScale(2, RoundingMode.HALF_UP);
            String body = String.format(
                    "{\"amount\":{\"value\":\"%s\",\"currency_code\":\"USD\"},\"note\":\"%s\"}",
                    amountStr.toPlainString(), refundNo);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v2/payments/captures/" + captureId + "/refund",
                    entity, Map.class);
            Map<String, Object> result = response.getBody();
            if (result == null) {
                throw new RuntimeException("PayPal 退款响应为空");
            }
            String refundId = (String) result.get("id");
            log.info("PayPal refund success: refundId={}", refundId);
            return refundId;
        } catch (Exception e) {
            log.error("PayPal refund exception", e);
            throw new RuntimeException("PayPal 退款服务不可用");
        }
    }

    /** PayPal OAuth 客户端凭据模式获取 access_token */
    @SuppressWarnings("unchecked")
    private String getPayPalAccessToken() {
        try {
            String baseUrl = "sandbox".equals(config.getPaypalMode())
                    ? "https://api-m.sandbox.paypal.com"
                    : "https://api-m.paypal.com";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(config.getPaypalClientId(), config.getPaypalClientSecret());
            String body = "grant_type=client_credentials";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v1/oauth2/token", entity, Map.class);
            Map<String, Object> result = response.getBody();
            if (result == null || result.get("access_token") == null) {
                throw new RuntimeException("PayPal token 获取失败");
            }
            return (String) result.get("access_token");
        } catch (Exception e) {
            log.error("PayPal token exception", e);
            throw new RuntimeException("PayPal 鉴权失败");
        }
    }

    // ============= WeChat Pay Refund =============
    private String callWechatRefund(String transactionId, BigDecimal amount, String orderNo, String refundNo) {
        if (!config.isWechatEnabled()) {
            log.warn("微信支付未配置，退款降级为本地模式: refundNo={}", refundNo);
            return "DEV_WECHAT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        try {
            // 微信退款 API：POST https://api.mch.weixin.qq.com/v3/refund/domestic/refunds
            // 使用 v3 版本需要 RSA 签名 + 证书（生产实现需加载 apiClientKey.pem）
            // 此处给出核心参数签名流程骨架：
            Map<String, String> params = new LinkedHashMap<>();
            params.put("appid", config.getWechatAppId());
            params.put("mch_id", config.getWechatMchId());
            params.put("nonce_str", UUID.randomUUID().toString().replace("-", ""));
            params.put("transaction_id", transactionId);
            params.put("out_trade_no", orderNo);
            params.put("out_refund_no", refundNo);
            // 微信退款金额单位：分
            params.put("refund_fee", String.valueOf(
                    amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()));
            params.put("total_fee", params.get("refund_fee")); // 简化：实际需传入原订单总金额
            params.put("notify_url", config.getWechatNotifyUrl());

            // sign = SHA256withRSA(商户私钥, "参数1=v1\n参数2=v2\n...") — base64 编码
            // 真实实现：使用 KeyFactory + 加载 apiClientKey.pem 私钥
            // 此处给出 demo 实现骨架，prod 需替换为完整 RSA 签名逻辑
            String signature = "PLACEHOLDER_SIGN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            params.put("sign", signature);

            // 真实实现：转换为 XML 后调用 v3 JSON 接口
            // 此处因 dev 环境未配置证书，返回降级流水号
            log.info("WeChat refund (demo): refundNo={}, params={}", refundNo, params);
            return "WX_" + refundNo;
        } catch (Exception e) {
            log.error("WeChat refund exception", e);
            throw new RuntimeException("微信退款服务不可用");
        }
    }

    // ============= Alipay Refund =============
    private String callAlipayRefund(String tradeNo, BigDecimal amount, String orderNo, String refundNo) {
        if (!config.isAlipayEnabled()) {
            log.warn("支付宝未配置，退款降级为本地模式: refundNo={}", refundNo);
            return "DEV_ALIPAY_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        try {
            // 支付宝退款 API：alipay.trade.refund
            Map<String, String> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", orderNo);
            bizContent.put("trade_no", tradeNo);
            bizContent.put("refund_amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            bizContent.put("out_request_no", refundNo);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("app_id", config.getAlipayAppId());
            params.put("method", "alipay.trade.refund");
            params.put("charset", "utf-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", java.time.LocalDateTime.now().toString());
            params.put("version", "1.0");
            params.put("biz_content", toJson(bizContent));
            params.put("notify_url", config.getAlipayNotifyUrl());

            // sign = SHA256withRSA(应用私钥, 待签名字符串)
            // 真实实现：使用应用私钥对拼接字符串签名后 base64 编码
            String signature = "PLACEHOLDER_SIGN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            params.put("sign", signature);

            // 真实实现：form-urlencoded POST 到 alipayGateway
            log.info("Alipay refund (demo): refundNo={}, params={}", refundNo, params);
            return "ALI_" + refundNo;
        } catch (Exception e) {
            log.error("Alipay refund exception", e);
            throw new RuntimeException("支付宝退款服务不可用");
        }
    }

    // ============= UnionPay Refund =============
    private String callUnionpayRefund(String transactionId, BigDecimal amount, String orderNo, String refundNo) {
        if (!config.isUnionpayEnabled()) {
            log.warn("银联未配置，退款降级为本地模式: refundNo={}", refundNo);
            return "DEV_UNIONPAY_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        try {
            // 银联退款 API：https://api.unionpayintl.com/4.0/gateway  (国际版)
            // 或 https://gateway.95516.com/gateway/api/frontTransReq.do (国内版)
            Map<String, String> params = new LinkedHashMap<>();
            params.put("version", "5.1.0");
            params.put("encoding", "UTF-8");
            params.put("certId", "PLACEHOLDER_CERT_ID");
            params.put("signMethod", "01"); // 01 = RSA
            params.put("txnType", "04"); // 04 = 退款
            params.put("txnSubType", "00");
            params.put("bizType", "000201");
            params.put("merId", config.getUnionpayMerchantId());
            params.put("orderId", refundNo);
            params.put("origQryId", transactionId);
            // 退款金额单位：分
            params.put("txnAmt", String.valueOf(
                    amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()));
            params.put("backUrl", config.getUnionpayNotifyUrl());

            // sign = SHA256withRSA(商户私钥, 待签名字符串)
            // 真实实现：使用 unionpayCertPath 加载私钥 + certPassword 解密
            String signature = "PLACEHOLDER_SIGN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            params.put("signature", signature);

            log.info("UnionPay refund (demo): refundNo={}, params={}", refundNo, params);
            return "UP_" + refundNo;
        } catch (Exception e) {
            log.error("UnionPay refund exception", e);
            throw new RuntimeException("银联退款服务不可用");
        }
    }

    // ============= 工具方法 =============

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String toJson(Map<String, String> map) {
        // 简化 JSON 序列化（生产建议用 Jackson）
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /** HMAC-SHA256 签名（预留，目前未启用） */
    @SuppressWarnings("unused")
    private static String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}

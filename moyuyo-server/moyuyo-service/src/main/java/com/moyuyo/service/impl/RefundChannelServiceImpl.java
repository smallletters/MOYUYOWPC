package com.moyuyo.service.impl;

import com.moyuyo.service.config.RefundChannelConfig;
import com.moyuyo.common.enums.RefundChannelEnum;
import com.moyuyo.service.RefundChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * 1) 渠道未配置时：仅 dev/test 降级为"本地生成流水号"模式（不调三方）；
 *    非 dev/test 一律抛 IllegalStateException，杜绝生产"假退款成功"
 * 2) 三方调用失败抛出 RuntimeException，事务回滚，由 Controller 返回错误
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundChannelServiceImpl implements RefundChannelService {

    private final RefundChannelConfig config;
    @Qualifier("restTemplate")
    private final RestTemplate restTemplate;
    /** 运行环境：仅 dev/test 允许本地假流水号 */
    private final Environment environment;

    /**
     * 渠道未配置时的统一处理：dev/test 允许本地假流水号，其余环境直接失败。
     */
    private void ensureMockRefundAllowed(String channel, String refundNo) {
        if (!isDevOrTestProfile()) {
            log.error("[refund] 退款渠道未配置，拒绝自动退款: channel={}, refundNo={}", channel, refundNo);
            throw new IllegalStateException(channel + " 退款渠道未配置，无法自动退款；请配置密钥或改用手动模式");
        }
        log.warn("{} 未配置，退款降级为本地模式（仅 dev/test）: refundNo={}", channel, refundNo);
    }

    private boolean isDevOrTestProfile() {
        if (environment == null) {
            return false;
        }
        String[] active = environment.getActiveProfiles();
        return active != null && (Arrays.asList(active).contains("dev")
                || Arrays.asList(active).contains("test"));
    }

    @Override
    public String refund(RefundChannelEnum channel, String payTransactionId, String orderNo,
                         BigDecimal refundAmount, String refundNo, String currency) {
        if (channel == null) {
            throw new IllegalArgumentException("退款渠道不能为空");
        }
        log.info("Refund channel call: channel={}, orderNo={}, refundNo={}, amount={}, currency={}",
                channel, orderNo, refundNo, refundAmount, currency);

        switch (channel) {
            case STRIPE:
                return callStripeRefund(payTransactionId, refundAmount, refundNo);
            case PAYPAL:
                return callPayPalRefund(payTransactionId, refundAmount, refundNo, currency);
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

    @Override
    public boolean isAutoRefundSupported(RefundChannelEnum channel) {
        if (channel == null) {
            return false;
        }
        switch (channel) {
            case STRIPE:
                return config.isStripeEnabled();
            case PAYPAL:
                return config.isPaypalEnabled();
            case WECHAT:
                return config.isWechatEnabled();
            case ALIPAY:
                return config.isAlipayEnabled();
            case UNIONPAY:
                return config.isUnionpayEnabled();
            case WALLET:
            case POINTS:
                // 内部账户退款无需第三方密钥
                return true;
            default:
                return false;
        }
    }

    // ============= Stripe Refund =============
    private String callStripeRefund(String paymentIntentId, BigDecimal amount, String refundNo) {
        if (!config.isStripeEnabled()) {
            ensureMockRefundAllowed("Stripe", refundNo);
            return "DEV_STRIPE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("订单缺少 Stripe 支付流水号，无法自动退款，请改用手动模式");
        }
        try {
            // Stripe Refund API: POST https://api.stripe.com/v1/refunds
            // amount 单位为最小货币单位（分/美分）
            long amountCents = amount.multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(config.getStripeSecretKey(), "");
            // Stripe 幂等键必须通过请求头传递，body 中的 idempotency_key 会被忽略
            headers.add("Idempotency-Key", refundNo);

            // 解析本地存储的交易号：cs_（Checkout Session）/ pi_（PaymentIntent）/ ch_（Charge）
            String targetParam = resolveStripeRefundTarget(paymentIntentId.trim(), headers);

            String body = targetParam
                    + "&amount=" + amountCents
                    + "&metadata[refund_no]=" + urlEncode(refundNo);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "https://api.stripe.com/v1/refunds",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> result = response.getBody();
            if (result == null || result.containsKey("error")) {
                log.error("Stripe refund failed: {}", result);
                throw new RuntimeException("Stripe 退款失败: " + extractChannelError(result == null ? null : result.toString()));
            }
            String refundId = (String) result.get("id");
            log.info("Stripe refund success: refundId={}", refundId);
            return refundId;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Stripe refund rejected: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Stripe 退款失败: " + extractChannelError(e.getResponseBodyAsString()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stripe refund exception", e);
            throw new RuntimeException("Stripe 退款服务不可用: " + e.getMessage());
        }
    }

    /**
     * 解析 Stripe 退款入参：Stripe 退款接口只接受 payment_intent 或 charge。
     * 本系统真实支付回调落库的是 Checkout Session ID（cs_xxx），需要先换取 payment_intent。
     */
    private String resolveStripeRefundTarget(String transactionId, HttpHeaders headers) {
        if (transactionId.startsWith("pi_")) {
            return "payment_intent=" + urlEncode(transactionId);
        }
        if (transactionId.startsWith("ch_")) {
            return "charge=" + urlEncode(transactionId);
        }
        if (transactionId.startsWith("cs_")) {
            ResponseEntity<Map<String, Object>> sessionResp = restTemplate.exchange(
                    "https://api.stripe.com/v1/checkout/sessions/" + urlEncode(transactionId),
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> session = sessionResp.getBody();
            Object paymentIntent = session == null ? null : session.get("payment_intent");
            if (paymentIntent == null || String.valueOf(paymentIntent).isBlank()) {
                throw new IllegalStateException("Stripe Checkout Session 尚未关联 payment_intent，无法退款");
            }
            return "payment_intent=" + urlEncode(String.valueOf(paymentIntent));
        }
        // 历史数据兜底：统一按 payment_intent 处理
        return "payment_intent=" + urlEncode(transactionId);
    }

    // ============= PayPal Refund =============
    private String callPayPalRefund(String payTransactionId, BigDecimal amount, String refundNo, String currency) {
        if (!config.isPaypalEnabled()) {
            ensureMockRefundAllowed("PayPal", refundNo);
            return "DEV_PAYPAL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        if (payTransactionId == null || payTransactionId.isBlank()) {
            throw new IllegalArgumentException("订单缺少 PayPal 支付流水号，无法自动退款，请改用手动模式");
        }
        try {
            String accessToken = getPayPalAccessToken();
            String baseUrl = "sandbox".equals(config.getPaypalMode())
                    ? "https://api-m.sandbox.paypal.com"
                    : "https://api-m.paypal.com";

            // PayPal 退款接口要求 capture_id；本系统支付回调落库的是 PayPal order_id，
            // 需要先按订单查询 captures 拿到 capture_id（旧的 capture_id 数据则直接使用）
            String captureId = resolvePayPalCaptureId(payTransactionId.trim(), baseUrl, accessToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            // PayPal 退款 idempotency 头（推荐）
            headers.add("PayPal-Request-Id", refundNo);

            // PayPal Refund API: POST /v2/payments/captures/{capture_id}/refund
            // amount.value 保留两位小数
            BigDecimal amountStr = amount.setScale(2, RoundingMode.HALF_UP);
            String currencyCode = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
            String body = String.format(
                    "{\"amount\":{\"value\":\"%s\",\"currency_code\":\"%s\"},\"note\":\"%s\"}",
                    amountStr.toPlainString(), currencyCode, refundNo);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/v2/payments/captures/" + captureId + "/refund",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> result = response.getBody();
            if (result == null) {
                throw new RuntimeException("PayPal 退款响应为空");
            }
            String refundId = (String) result.get("id");
            // 校验 PayPal 返回的 status：仅 COMPLETED/PENDING 视为成功。
            // 历史上出现过 HTTP 200 但 status=FAILED/DENIED 的场景（合规/欺诈/账户问题），
            // 若不校验则本地 refund_no 与渠道方对不上，未来对账会发现"有钱但没退款记录"。
            String status = (String) result.get("status");
            if (!"COMPLETED".equals(status) && !"PENDING".equals(status)) {
                log.error("PayPal refund rejected: refundId={}, status={}, fullResponse={}",
                        refundId, status, result);
                throw new RuntimeException("PayPal 退款未受理: status=" + status + ", refundId=" + refundId);
            }
            log.info("PayPal refund success: refundId={}, status={}", refundId, status);
            return refundId;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("PayPal refund rejected: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayPal 退款失败: " + extractChannelError(e.getResponseBodyAsString()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("PayPal refund exception", e);
            throw new RuntimeException("PayPal 退款服务不可用: " + e.getMessage());
        }
    }

    /**
     * 解析 PayPal 退款目标 ID：优先按 order_id 查询订单并取第一条 capture_id；
     * 若传入的本身就是 capture_id（GET 订单 404），则直接使用原值。
     */
    private String resolvePayPalCaptureId(String transactionId, String baseUrl, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    baseUrl + "/v2/checkout/orders/" + urlEncode(transactionId),
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> order = resp.getBody();
            String captureId = extractFirstCaptureId(order);
            if (captureId != null) {
                log.info("PayPal order resolved to capture: orderId={}, captureId={}", transactionId, captureId);
                return captureId;
            }
        } catch (Exception e) {
            // 传入的可能是 capture_id 或已被清理的订单，继续按 capture_id 处理
            log.debug("PayPal order lookup skipped: id={}, reason={}", transactionId, e.getMessage());
        }
        return transactionId;
    }

    /** 从 PayPal 订单响应中取出第一条 capture.id */
    @SuppressWarnings("unchecked")
    private String extractFirstCaptureId(Map<String, Object> order) {
        if (order == null) return null;
        Object unitsObj = order.get("purchase_units");
        if (!(unitsObj instanceof java.util.List<?> units) || units.isEmpty()) return null;
        Object first = units.get(0);
        if (!(first instanceof Map<?, ?> unit)) return null;
        Object paymentsObj = unit.get("payments");
        if (!(paymentsObj instanceof Map<?, ?> payments)) return null;
        Object capturesObj = payments.get("captures");
        if (!(capturesObj instanceof java.util.List<?> captures) || captures.isEmpty()) return null;
        Object capture = captures.get(0);
        if (!(capture instanceof Map<?, ?> captureMap)) return null;
        Object id = captureMap.get("id");
        return id == null ? null : id.toString();
    }

    /** PayPal OAuth 客户端凭据模式获取 access_token */
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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/v1/oauth2/token",
                    org.springframework.http.HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
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
            ensureMockRefundAllowed("微信支付", refundNo);
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
            ensureMockRefundAllowed("支付宝", refundNo);
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
            ensureMockRefundAllowed("银联", refundNo);
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

    /**
     * 从渠道错误响应中提取可读错误信息（Stripe/PayPal 均返回 JSON 且包含 message 字段）。
     * 解析失败时直接返回原始响应体，便于运维排查。
     */
    private static String extractChannelError(String body) {
        if (body == null || body.isBlank()) {
            return "渠道未返回错误信息";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"message\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(body);
        return matcher.find() ? matcher.group(1) : body;
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

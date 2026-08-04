package com.moyuyo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.annotation.OperationLog;
import com.moyuyo.common.dto.payment.CreatePaymentRequest;
import com.moyuyo.common.dto.payment.CreatePaymentResponse;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.PaymentService;
import static com.moyuyo.common.enums.OrderStatusEnum.*;
import static com.moyuyo.common.enums.PaymentStatusEnum.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:webhook:";

    /** Stripe webhook 签名时间戳允许偏差（秒），避免时钟漂移导致的合法请求被拒 */
    private static final long STRIPE_SIGNATURE_TOLERANCE = 300;

    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    /** 全局复用的 Jackson 实例，避免每次 new ObjectMapper 浪费资源 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentServiceImpl(OrderService orderService,
                               PaymentMapper paymentMapper,
                               OrderMapper orderMapper,
                               @Qualifier("restTemplate") RestTemplate restTemplate,
                               StringRedisTemplate redisTemplate) {
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Value("${payment.stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${payment.stripe.webhook-secret}")
    private String stripeWebhookSecret;

    @Value("${payment.stripe.currency:usd}")
    private String stripeCurrency;

    @Value("${payment.paypal.client-id}")
    private String paypalClientId;

    @Value("${payment.paypal.client-secret}")
    private String paypalClientSecret;

    @Value("${payment.paypal.mode:sandbox}")
    private String paypalMode;

    @Value("${payment.paypal.webhook-id}")
    private String paypalWebhookId;

    @Value("${payment.paypal.allowed-origins:https://moyuyo.com}")
    private String paypalAllowedOrigins;

    @Override
    @Transactional
    public CreatePaymentResponse createPayment(Long userId, CreatePaymentRequest request) {
        OrderEntity order = orderService.getOrderByOrderNo(request.getOrderNo());
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + request.getOrderNo());
        }
        // 越权校验：仅订单所属用户可发起支付
        if (!Objects.equals(order.getUserId(), userId)) {
            // 安全审计日志：记录越权尝试，便于风控
            log.warn("Payment authorization denied: userId={}, orderNo={}, orderUserId={}",
                    userId, request.getOrderNo(), order.getUserId());
            throw new IllegalArgumentException("无权支付该订单");
        }
        if (!PENDING_PAY.name().equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不允许支付: " + order.getStatus());
        }

        if ("STRIPE".equalsIgnoreCase(request.getPayChannel())) {
            return createStripePayment(order, request);
        } else if ("PAYPAL".equalsIgnoreCase(request.getPayChannel())) {
            return createPayPalPayment(order, request);
        } else {
            throw new IllegalArgumentException("不支持的支付渠道: " + request.getPayChannel());
        }
    }

    private CreatePaymentResponse createStripePayment(OrderEntity order, CreatePaymentRequest request) {
        String amountCents = order.getPayAmount().multiply(java.math.BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP).toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(stripeSecretKey, "");

        String description = URLEncoder.encode("MOYUYO Order " + order.getOrderNo(), StandardCharsets.UTF_8);
        String body = "amount=" + amountCents
                + "&currency=" + stripeCurrency
                + "&description=" + description
                + "&metadata[order_no]=" + order.getOrderNo();

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.stripe.com/v1/payment_intents",
                    entity,
                    Map.class);

            Map<String, Object> result = response.getBody();
            if (result == null || result.containsKey("error")) {
                log.error("Stripe payment intent creation failed: {}", result);
                throw new RuntimeException("Stripe payment creation failed");
            }

            String paymentIntentId = (String) result.get("id");
            String clientSecret = (String) result.get("client_secret");

            savePaymentRecord(order.getId(), "STRIPE", paymentIntentId, order.getPayAmount());

            log.info("Stripe payment created: paymentIntentId={}, orderNo={}", paymentIntentId, order.getOrderNo());
            return new CreatePaymentResponse(paymentIntentId, clientSecret, null, "STRIPE");
        } catch (Exception e) {
            log.error("Stripe API call failed", e);
            // 不向调用方暴露底层异常细节，防止信息泄露
            throw new RuntimeException("Stripe payment service unavailable");
        }
    }

    private CreatePaymentResponse createPayPalPayment(OrderEntity order, CreatePaymentRequest request) {
        String accessToken = getPayPalAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        String baseUrl = "sandbox".equals(paypalMode)
                ? "https://api-m.sandbox.paypal.com"
                : "https://api-m.paypal.com";

        // returnUrl 白名单校验：防止开放重定向钓鱼
        String successUrl = validateAndBuildReturnUrl(request.getReturnUrl(), "success");
        String cancelUrl = validateAndBuildReturnUrl(request.getReturnUrl(), "cancel");

        String orderJson = String.format("""
                {
                  "intent": "CAPTURE",
                  "purchase_units": [{
                    "reference_id": "%s",
                    "description": "MOYUYO Order",
                    "amount": {
                      "currency_code": "USD",
                      "value": "%s"
                    }
                  }],
                  "payment_source": {
                    "paypal": {
                      "experience_context": {
                        "payment_method_preference": "IMMEDIATE_PAYMENT_REQUIRED",
                        "landing_page": "LOGIN",
                        "user_action": "PAY_NOW",
                        "return_url": "%s",
                        "cancel_url": "%s"
                      }
                    }
                  }
                }""", order.getOrderNo(), order.getPayAmount().toPlainString(),
                successUrl, cancelUrl);

        HttpEntity<String> entity = new HttpEntity<>(orderJson, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v2/checkout/orders",
                    entity,
                    Map.class);

            Map<String, Object> result = response.getBody();
            if (result == null) {
                throw new RuntimeException("PayPal order creation failed");
            }

            String paypalOrderId = (String) result.get("id");

            String approvalUrl = null;
            java.util.List<Map<String, Object>> links = (java.util.List<Map<String, Object>>) result.get("links");
            if (links != null) {
                for (Map<String, Object> link : links) {
                    if ("payer-action".equals(link.get("rel"))) {
                        approvalUrl = (String) link.get("href");
                        break;
                    }
                }
            }

            savePaymentRecord(order.getId(), "PAYPAL", paypalOrderId, order.getPayAmount());

            log.info("PayPal order created: paypalOrderId={}, orderNo={}", paypalOrderId, order.getOrderNo());
            return new CreatePaymentResponse(paypalOrderId, null, approvalUrl, "PAYPAL");
        } catch (Exception e) {
            log.error("PayPal API call failed", e);
            // 不向调用方暴露底层异常细节，防止信息泄露
            throw new RuntimeException("PayPal payment service unavailable");
        }
    }

    private String getPayPalAccessToken() {
        String baseUrl = "sandbox".equals(paypalMode)
                ? "https://api-m.sandbox.paypal.com"
                : "https://api-m.paypal.com";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String auth = Base64.getEncoder().encodeToString(
                (paypalClientId + ":" + paypalClientSecret).getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + auth);

        HttpEntity<String> entity = new HttpEntity<>("grant_type=client_credentials", headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v1/oauth2/token", entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("access_token")) {
                throw new RuntimeException("Failed to get PayPal access token");
            }
            return (String) body.get("access_token");
        } catch (Exception e) {
            log.error("PayPal access token request failed", e);
            throw new RuntimeException("PayPal authentication failed");
        }
    }

    @Override
    @Transactional
    @OperationLog(type = "支付回调", detail = "#payChannel", logParams = false)
    public void handleWebhook(String payChannel, String payload, Map<String, String> headers) {
        // 验签：支付回调必须校验来源，防止伪造
        boolean signatureValid;
        if ("STRIPE".equalsIgnoreCase(payChannel)) {
            signatureValid = verifyStripeSignature(payload, headers.get("stripe-signature"));
        } else if ("PAYPAL".equalsIgnoreCase(payChannel)) {
            signatureValid = verifyPayPalSignature(payload, headers);
        } else {
            log.warn("Unknown webhook channel: {}", payChannel);
            // 未知渠道返回 200 但不处理（webhook 通道不向外界暴露校验结果）
            return;
        }
        // 重要：签名失败时绝对不能抛异常返回 4xx，否则会被支付渠道无限重试并消耗资源。
        // 这里仅记录告警日志，让 Gateway 重试时再次校验（攻击者无法拿到我们的 webhook secret）。
        if (!signatureValid) {
            log.warn("Webhook 签名校验失败: channel={}, 丢弃该事件（支付渠道会按策略重试）", payChannel);
            return;
        }

        // 提取事件 ID 用于幂等检查
        String eventId = extractEventId(payload);
        if (eventId != null) {
            String idempotentKey = IDEMPOTENT_KEY_PREFIX + eventId;
            // Redis SET NX：仅当 key 不存在时设置成功，TTL 24 小时
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", 24, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(acquired)) {
                log.info("Webhook 已处理，幂等跳过: eventId={}, channel={}", eventId, payChannel);
                return;
            }
        }

        if ("STRIPE".equalsIgnoreCase(payChannel)) {
            handleStripeWebhook(payload);
        } else if ("PAYPAL".equalsIgnoreCase(payChannel)) {
            handlePayPalWebhook(payload);
        }
    }

    /**
     * Stripe webhook 验签
     * Stripe-Signature 头格式：t=timestamp,v1=signature
     * 签名算法：HMAC-SHA256(timestamp + "." + payload, webhook_secret) 转 hex
     */
    private boolean verifyStripeSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String timestamp = null;
        List<String> v1Signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            String value = kv[1].trim();
            if ("t".equals(key)) {
                timestamp = value;
            } else if ("v1".equals(key)) {
                v1Signatures.add(value);
            }
        }
        if (timestamp == null || v1Signatures.isEmpty()) {
            return false;
        }
        // 防止重放攻击：拒绝超过 5 分钟的旧事件
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - ts) > STRIPE_SIGNATURE_TOLERANCE) {
                log.warn("Stripe webhook 时间戳漂移超限: ts={}, now={}", ts, now);
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        String signedPayload = timestamp + "." + payload;
        String computed = hmacSha256Hex(signedPayload, stripeWebhookSecret);
        // 常量时间比较，防止时序攻击
        for (String expected : v1Signatures) {
            if (MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /**
     * PayPal webhook 验签
     * 调用 PayPal /v1/notifications/verify-webhook-signature 接口校验
     */
    private boolean verifyPayPalSignature(String payload, Map<String, String> headers) {
        String transmissionId = headers.get("paypal-transmission-id");
        String transmissionTime = headers.get("paypal-transmission-time");
        String certUrl = headers.get("paypal-cert-url");
        String authAlgo = headers.get("paypal-auth-algo");
        String transmissionSig = headers.get("paypal-transmission-sig");
        if (transmissionId == null || transmissionSig == null || certUrl == null) {
            return false;
        }
        // certUrl 必须来自 PayPal 域，防止伪造证书
        if (!certUrl.startsWith("https://api-m.paypal.com/") && !certUrl.startsWith("https://api-m.sandbox.paypal.com/")) {
            log.warn("PayPal webhook 证书 URL 不在白名单内: {}", certUrl);
            return false;
        }

        String accessToken;
        try {
            accessToken = getPayPalAccessToken();
        } catch (Exception e) {
            log.error("PayPal webhook 验签：获取 access_token 失败", e);
            return false;
        }
        String baseUrl = "sandbox".equals(paypalMode)
                ? "https://api-m.sandbox.paypal.com"
                : "https://api-m.paypal.com";

        // 构造验签请求体（webhook_event 字段直接嵌入原始 payload）
        String verifyBody = String.format(
                "{\"transmission_id\":\"%s\",\"transmission_time\":\"%s\",\"cert_url\":\"%s\","
                        + "\"auth_algo\":\"%s\",\"transmission_sig\":\"%s\",\"webhook_id\":\"%s\","
                        + "\"webhook_event\":%s}",
                transmissionId, transmissionTime, certUrl,
                authAlgo != null ? authAlgo : "SHA256withRSA",
                transmissionSig, paypalWebhookId, payload);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(verifyBody, httpHeaders);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v1/notifications/verify-webhook-signature",
                    entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return false;
            }
            return "SUCCESS".equals(body.get("verification_status"));
        } catch (Exception e) {
            log.error("PayPal webhook 验签请求失败", e);
            return false;
        }
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 计算失败", e);
        }
    }

    /**
     * 校验 returnUrl 是否在白名单域名内，并拼接最终回调 URL
     * 防止开放重定向钓鱼攻击
     * <p>
     * 安全要点：使用 URI.getHost() 与白名单逐项比较，避免字符串包含匹配（如 moyuyo.com.attacker.com 不会被误判）。
     */
    private String validateAndBuildReturnUrl(String returnUrl, String suffix) {
        // 默认回调地址
        String defaultUrl = "https://moyuyo.com/order/" + suffix;
        if (returnUrl == null || returnUrl.isBlank()) {
            return defaultUrl;
        }
        try {
            URI uri = URI.create(returnUrl);
            // scheme 必须为 https，避免 http:// 内网跳转泄漏
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                log.warn("PayPal returnUrl scheme 不合法: {}", uri.getScheme());
                return defaultUrl;
            }
            String host = uri.getHost();
            if (host == null) {
                return defaultUrl;
            }
            // 主机名归一化（小写）后逐项精确比较
            String normalizedHost = host.toLowerCase();
            for (String allowed : paypalAllowedOrigins.split(",")) {
                String allowedHost = allowed.trim()
                        .replaceFirst("^https?://", "")
                        .replaceFirst("/.*$", "")
                        .toLowerCase();
                // 精确匹配，避免子域名绕过（如 attacker.moyuyo.com 与 moyuyo.com 必须区分）
                if (normalizedHost.equals(allowedHost)) {
                    return returnUrl;
                }
            }
            log.warn("PayPal returnUrl 不在白名单内，使用默认地址: host={}", host);
            return defaultUrl;
        } catch (Exception e) {
            return defaultUrl;
        }
    }

    /**
     * 从 webhook payload 中提取事件唯一 ID
     * Stripe: event.id
     * PayPal: event.id
     */
    private String extractEventId(String payload) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            return (String) event.get("id");
        } catch (Exception e) {
            log.warn("提取 webhook 事件 ID 失败", e);
            return null;
        }
    }

    private void handleStripeWebhook(String payload) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            String type = (String) event.get("type");

            if (!"payment_intent.succeeded".equals(type)) {
                log.debug("Ignoring Stripe webhook event: {}", type);
                return;
            }

            Map<String, Object> data = (Map<String, Object>) event.get("data");
            Map<String, Object> object = (Map<String, Object>) (data != null ? data.get("object") : null);
            if (object == null) return;

            String paymentIntentId = (String) object.get("id");
            Map<String, String> metadata = (Map<String, String>) object.get("metadata");
            String orderNo = metadata != null ? metadata.get("order_no") : null;

            if (orderNo == null) {
                PaymentEntity payment = paymentMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentEntity>()
                                .eq(PaymentEntity::getTransactionId, paymentIntentId));
                if (payment != null) {
                    OrderEntity order = orderMapper.selectById(payment.getOrderId());
                    if (order != null) {
                        orderNo = order.getOrderNo();
                    }
                }
            }

            if (orderNo != null) {
                orderService.payCallback(orderNo, "STRIPE", paymentIntentId);
                log.info("Stripe webhook processed: orderNo={}, paymentIntentId={}", orderNo, paymentIntentId);
            } else {
                log.warn("Stripe webhook 找不到订单: paymentIntentId={}", paymentIntentId);
            }
        } catch (Exception e) {
            // webhook 处理异常不能向上抛（已签名通过，应由重试机制保证最终一致）
            log.error("Failed to process Stripe webhook", e);
        }
    }

    private void handlePayPalWebhook(String payload) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);
            String eventType = (String) event.get("event_type");

            if (!"CHECKOUT.ORDER.APPROVED".equals(eventType) && !"PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
                log.debug("Ignoring PayPal webhook event: {}", eventType);
                return;
            }

            Map<String, Object> resource = (Map<String, Object>) event.get("resource");
            if (resource == null) return;

            String paypalOrderId = (String) resource.get("id");
            if (paypalOrderId == null) {
                java.util.List<Map<String, Object>> links = (java.util.List<Map<String, Object>>) resource.get("links");
                if (links != null) {
                    for (Map<String, Object> link : links) {
                        String href = (String) link.get("href");
                        if (href != null && href.contains("/v2/checkout/orders/")) {
                            String extracted = href.substring(href.indexOf("/v2/checkout/orders/") + 20);
                            // 只截取到下一个 ? 或 / 之前，避免把后续路径/参数当成 ID
                            int cut = extracted.indexOf('?');
                            if (cut > 0) {
                                extracted = extracted.substring(0, cut);
                            }
                            int slash = extracted.indexOf('/');
                            if (slash > 0) {
                                extracted = extracted.substring(0, slash);
                            }
                            // PayPal 订单 ID 通常为 17 位字母数字，过短/过长都视为非法
                            if (extracted.length() >= 10 && extracted.length() <= 32) {
                                paypalOrderId = extracted;
                            }
                            break;
                        }
                    }
                }
            }

            if (paypalOrderId != null) {
                PaymentEntity payment = paymentMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentEntity>()
                                .eq(PaymentEntity::getTransactionId, paypalOrderId));
                if (payment != null) {
                    OrderEntity order = orderMapper.selectById(payment.getOrderId());
                    if (order != null) {
                        String orderNo = order.getOrderNo();
                        orderService.payCallback(orderNo, "PAYPAL", paypalOrderId);
                        log.info("PayPal webhook processed: orderNo={}, paypalOrderId={}", orderNo, paypalOrderId);
                    }
                } else {
                    log.warn("PayPal webhook 找不到对应支付记录: paypalOrderId={}", paypalOrderId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process PayPal webhook", e);
        }
    }

    private void savePaymentRecord(Long orderId, String payChannel, String transactionId, java.math.BigDecimal amount) {
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(orderId);
        payment.setPayChannel(payChannel);
        payment.setTransactionId(transactionId);
        payment.setAmount(amount);
        payment.setCurrency("USD");
        payment.setStatus(PENDING.name());
        payment.setPaidAt(null);
        paymentMapper.insert(payment);
    }
}

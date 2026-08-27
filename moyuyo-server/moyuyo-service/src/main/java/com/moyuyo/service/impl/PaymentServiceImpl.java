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
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import static com.moyuyo.common.enums.OrderStatusEnum.*;
import static com.moyuyo.common.enums.PaymentStatusEnum.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:webhook:";

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

    @Value("${payment.stripe.publishable-key:}")
    private String stripePublishableKey;

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

    /**
     * H1 修复：Bean 初始化时设置 Stripe SDK 全局 API key，避免每次调用重新设置。
     * 注意：Stripe.apiKey 是静态变量，多实例部署时所有实例共享同一 key（按设计如此）。
     */
    @PostConstruct
    public void initStripe() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
            log.info("[payment] Stripe SDK initialized, currency={}", stripeCurrency);
        } else {
            log.warn("[payment] Stripe secret key is empty, Stripe payments will fail");
        }
    }

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
        // H1 修复：使用 Stripe.checkout.Session 替代手写 PaymentIntent。
        // Checkout Session 由 Stripe 托管支付页，自动支持 Card / Apple Pay / Google Pay，
        // 前端 web-view 直接打开 session.url 即可（无需前端拼 URL）。
        // amount 单位为最小货币单位（USD = cents）
        long amountCents = order.getPayAmount().multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        if (amountCents <= 0) {
            throw new IllegalArgumentException("订单金额必须大于 0");
        }

        // successUrl / cancelUrl 走中转页，由中转页通过 postMessage 回传给 APP
        String baseUrl = buildPublicBaseUrl(request.getReturnUrl());
        String successUrl = baseUrl + "/payment/return.html?status=success&orderNo="
                + URLEncoder.encode(order.getOrderNo(), StandardCharsets.UTF_8);
        String cancelUrl = baseUrl + "/payment/return.html?status=cancel&orderNo="
                + URLEncoder.encode(order.getOrderNo(), StandardCharsets.UTF_8);

        try {
            // 构建 Checkout Session 参数（不可变 builder）
            // 注意：Stripe SDK 28.x 的 PaymentIntentData 接受 builder().build() 而非 lambda；
            // PaymentIntentData.Builder 没有 setMetadata(Map)，需逐个 putMetadata
            SessionCreateParams.PaymentIntentData paymentIntentData = SessionCreateParams.PaymentIntentData.builder()
                    .putMetadata("order_no", order.getOrderNo())
                    .build();
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(order.getOrderNo())
                    .putMetadata("order_no", order.getOrderNo())
                    .putMetadata("user_id", String.valueOf(order.getUserId()))
                    .setPaymentIntentData(paymentIntentData)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(stripeCurrency)
                                    .setUnitAmount(amountCents)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("MOYUYO Order " + order.getOrderNo())
                                            .setDescription("MOYUYO Order #" + order.getOrderNo())
                                            .build())
                                    .build())
                            .build())
                    .build();

            Session session = Session.create(params);

            // 落库 PaymentEntity（transactionId 存 session.id）
            savePaymentRecord(order.getId(), "STRIPE", session.getId(), order.getPayAmount());

            log.info("Stripe checkout session created: sessionId={}, url={}, orderNo={}",
                    session.getId(), session.getUrl(), order.getOrderNo());

            return new CreatePaymentResponse(
                    session.getId(),
                    null, // clientSecret 不再需要，Checkout 模式由 Stripe 托管
                    session.getUrl(),
                    stripePublishableKey,
                    null, // approvalUrl 仅 PayPal 用
                    "STRIPE"
            );
        } catch (StripeException e) {
            log.error("Stripe Checkout Session creation failed: code={}, message={}",
                    e.getCode(), e.getMessage(), e);
            throw new RuntimeException("Stripe payment service unavailable");
        } catch (Exception e) {
            log.error("Stripe payment creation failed unexpectedly", e);
            throw new RuntimeException("Stripe payment service unavailable");
        }
    }

    /**
     * 推算中转页基础域名：优先用入参 returnUrl 的 host（与 PayPal returnUrl 白名单共用同一校验路径），
     * 否则回落到默认 https://域名。
     */
    private String buildPublicBaseUrl(String returnUrl) {
        if (returnUrl != null && !returnUrl.isBlank()) {
            try {
                URI uri = URI.create(returnUrl);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(scheme) && host != null) {
                    return scheme + "://" + host;
                }
            } catch (Exception ignore) {
            }
        }
        return "https://moyuyo.com";
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
        // 与 Stripe 一致：把 orderNo 拼到 success/cancel URL 的 query，
        // 让 PayPal 跳转回 moyuyo.com 中转页时携带 orderNo，postMessage 转发到 APP
        String successUrl = validateAndBuildReturnUrl(
                request.getReturnUrl(), "success", order.getOrderNo());
        String cancelUrl = validateAndBuildReturnUrl(
                request.getReturnUrl(), "cancel", order.getOrderNo());

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
            return new CreatePaymentResponse(
                    paypalOrderId,
                    null, // clientSecret Stripe 专用
                    null, // sessionUrl Stripe 专用
                    null, // publishableKey Stripe 专用
                    approvalUrl,
                    "PAYPAL"
            );
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
            // H1 修复：用 Stripe SDK 的 Webhook.constructEvent 验签（包含时间戳容差校验）
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
     * H1 修复：用 Stripe SDK 官方 Webhook.constructEvent 验签，
     * 自动处理 Stripe-Signature 头解析、时间戳容差、HMAC-SHA256 验签。
     */
    private boolean verifyStripeSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            log.error("Stripe webhook secret is empty, cannot verify signature");
            return false;
        }
        try {
            Event event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
            return event != null;
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Stripe webhook verify unexpected error", e);
            return false;
        }
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

    /**
     * 校验 returnUrl 是否在白名单域名内，并拼接最终回调 URL
     * 防止开放重定向钓鱼攻击
     * <p>
     * 安全要点：使用 URI.getHost() 与白名单逐项比较，避免字符串包含匹配（如 moyuyo.com.attacker.com 不会被误判）。
     */
    private String validateAndBuildReturnUrl(String returnUrl, String suffix, String orderNo) {
        // 默认回调地址：与中转页路径一致，拼接 orderNo 让 APP 能识别当前订单
        String defaultUrl = "https://moyuyo.com/payment/return.html?status=" + suffix
                + "&orderNo=" + URLEncoder.encode(orderNo, StandardCharsets.UTF_8);
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
                    // 白名单域名通过：去掉原 returnUrl 上的 query（PayPal 会自己加 ?token=xxx），
                    // 改成 /payment/return.html + 自定义 status/orderNo 标识
                    String path = uri.getPath();
                    if (path == null || path.isEmpty()) {
                        path = "/payment/return.html";
                    }
                    return uri.getScheme() + "://" + host + path
                            + "?status=" + suffix
                            + "&orderNo=" + URLEncoder.encode(orderNo, StandardCharsets.UTF_8);
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

            // H1 修复：优先处理 Checkout Session 事件，与 createStripePayment 中 Stripe.checkout.Session 一致
            if ("checkout.session.completed".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                Map<String, Object> object = (Map<String, Object>) (data != null ? data.get("object") : null);
                if (object == null) return;
                String sessionId = (String) object.get("id");
                Object metadataObj = object.get("metadata");
                Map<String, String> metadata = metadataObj instanceof Map
                        ? (Map<String, String>) metadataObj : null;
                String orderNo = metadata != null ? metadata.get("order_no") : null;
                // 兜底：取 client_reference_id（创建 session 时设置的 orderNo）
                if (orderNo == null) {
                    orderNo = (String) object.get("client_reference_id");
                }
                if (orderNo == null) {
                    log.warn("Stripe checkout.session.completed missing orderNo, sessionId={}", sessionId);
                    return;
                }
                orderService.payCallback(orderNo, "STRIPE", sessionId);
                log.info("Stripe checkout.session.completed: orderNo={}, sessionId={}", orderNo, sessionId);
                return;
            }

            if ("checkout.session.expired".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                Map<String, Object> object = (Map<String, Object>) (data != null ? data.get("object") : null);
                if (object == null) return;
                String sessionId = (String) object.get("id");
                markPaymentFailedByTransactionId(sessionId, "checkout_session_expired");
                log.info("Stripe checkout.session.expired: sessionId={}", sessionId);
                return;
            }

            // 兼容老链路：直接用 PaymentIntent API 也能走通
            if ("payment_intent.succeeded".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                Map<String, Object> object = (Map<String, Object>) (data != null ? data.get("object") : null);
                if (object == null) return;
                String paymentIntentId = (String) object.get("id");
                Object metadataObj = object.get("metadata");
                Map<String, String> metadata = metadataObj instanceof Map
                        ? (Map<String, String>) metadataObj : null;
                String orderNo = resolveOrderNoByTransactionId(paymentIntentId,
                        metadata != null ? metadata.get("order_no") : null);
                if (orderNo != null) {
                    orderService.payCallback(orderNo, "STRIPE", paymentIntentId);
                    log.info("Stripe webhook processed: orderNo={}, paymentIntentId={}", orderNo, paymentIntentId);
                } else {
                    log.warn("Stripe webhook 找不到订单: paymentIntentId={}", paymentIntentId);
                }
                return;
            }

            if ("payment_intent.payment_failed".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                Map<String, Object> object = (Map<String, Object>) (data != null ? data.get("object") : null);
                if (object == null) return;
                String paymentIntentId = (String) object.get("id");
                String lastError = extractStripeErrorMessage(object);
                markPaymentFailedByTransactionId(paymentIntentId, lastError);
                log.warn("Stripe payment failed: paymentIntentId={}, reason={}", paymentIntentId, lastError);
                return;
            }

            if ("payment_intent.canceled".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                Map<String, Object> object = (Map<String, Object>) (data != null ? data.get("object") : null);
                if (object == null) return;
                String paymentIntentId = (String) object.get("id");
                markPaymentFailedByTransactionId(paymentIntentId, "canceled_by_stripe");
                log.info("Stripe payment canceled: paymentIntentId={}", paymentIntentId);
                return;
            }

            log.debug("Ignoring Stripe webhook event: {}", type);
        } catch (Exception e) {
            // webhook 处理异常不能向上抛（已签名通过，应由重试机制验证最终一致）
            log.error("Failed to process Stripe webhook", e);
        }
    }

    /**
     * 按 transactionId 解析订单号：优先取 metadata.order_no，其次按 transactionId 反查 PaymentEntity。
     */
    private String resolveOrderNoByTransactionId(String transactionId, String metadataOrderNo) {
        if (metadataOrderNo != null) {
            return metadataOrderNo;
        }
        return resolveOrderNoByTransactionId(transactionId);
    }

    /**
     * 仅按 transactionId 反查 PaymentEntity 找订单号（用于 Stripe Session 场景）。
     */
    private String resolveOrderNoByTransactionId(String transactionId) {
        if (transactionId == null) {
            return null;
        }
        PaymentEntity payment = paymentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentEntity>()
                        .eq(PaymentEntity::getTransactionId, transactionId));
        if (payment != null) {
            OrderEntity order = orderMapper.selectById(payment.getOrderId());
            if (order != null) {
                return order.getOrderNo();
            }
        }
        return null;
    }

    /**
     * 提取 Stripe payment_failed 事件中的错误描述，便于审计追溯。
     */
    private String extractStripeErrorMessage(Map<String, Object> object) {
        Object lastErr = object.get("last_payment_error");
        if (lastErr instanceof Map) {
            Object msg = ((Map<String, Object>) lastErr).get("message");
            if (msg != null) {
                return msg.toString();
            }
        }
        return "payment_failed";
    }

    /**
     * 把对应 PaymentEntity 标记为 FAILED；如果还没创建 PaymentEntity（用户未走到 createPayment）则忽略。
     * 不强行取消订单：让用户在前端看到 PENDING_PAY 时可重新发起支付，由 OrderTimeoutCancelJob 兜底取消。
     */
    private void markPaymentFailedByTransactionId(String transactionId, String reason) {
        if (transactionId == null) {
            return;
        }
        PaymentEntity payment = paymentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentEntity>()
                        .eq(PaymentEntity::getTransactionId, transactionId));
        if (payment == null) {
            return;
        }
        // 仅当 PaymentEntity 当前不是 SUCCESS 时才标记为 FAILED，避免覆盖已成功的支付
        if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            payment.setStatus("FAILED");
            paymentMapper.updateById(payment);
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

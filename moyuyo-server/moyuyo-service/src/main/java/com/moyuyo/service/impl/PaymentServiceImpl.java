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
import org.springframework.util.StringUtils;
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

    /**
     * 判断 Stripe/PayPal 密钥是否是占位符或未配置。
     * 如果未配置，进入 mock 模式返回假的 Checkout URL，便于本地调试和 APP 打包测试 UI 流程。
     * 命中条件：null / 空白 / 包含 "placeholder" / 以 "sk_test_placeholder" 等占位格式开头。
     */
    private boolean isPlaceholderKey(String key) {
        if (!StringUtils.hasText(key)) return true;
        String k = key.trim();
        if (k.toLowerCase().contains("placeholder")) return true;
        if (k.startsWith("sk_test_placeholder")) return true;
        if (k.startsWith("pk_test_placeholder")) return true;
        if (k.startsWith("whsec_placeholder")) return true;
        return false;
    }

    /**
     * 生成 mock 支付页 URL（仅用于密钥未配置时的本地/联调/APP 打包 UI 测试）。
     * 返回 return.html + status=success 参数，这样 H5 WebView 会显示"支付成功"，
     * APP 端通过 payAppBridge 拦截 URL 返回成功信号，完整演练"下单→跳转→回跳→订单刷新"全链路。
     * 金额/订单号保持一致，便于前端比对。
     */
    private String buildMockCheckoutUrl(CreatePaymentRequest request, String orderNo, boolean isApp, String schemeBase) {
        String orderNoEnc = URLEncoder.encode(orderNo, StandardCharsets.UTF_8);
        if (isApp && schemeBase != null) {
            return appendQuery(schemeBase, "status=success&orderNo=" + orderNoEnc
                    + "&_mock=1&method=" + (request.getPayMethod() == null ? "STRIPE" : request.getPayMethod()));
        }
        String base = buildPublicBaseUrl(request.getReturnUrl());
        return base + "/payment/return.html?status=success&orderNo=" + orderNoEnc
                + "&_mock=1&method=" + (request.getPayMethod() == null ? "STRIPE" : request.getPayMethod());
    }

    private CreatePaymentResponse createStripePayment(OrderEntity order, CreatePaymentRequest request) {
        // ========== Mock 兜底：密钥是占位符时直接返回假 Checkout URL ==========
        // 避免本地/联调环境没配真实密钥时前端报 "Stripe payment service unavailable"。
        // APP 端会用 moyuyo://pay/return?status=success 回跳，H5 端用 return.html?status=success 中转。
        boolean isApp = "APP".equalsIgnoreCase(request.getClientType());
        String schemeBase = null;
        if (isApp) {
            schemeBase = StringUtils.hasText(request.getSchemeBase())
                    ? request.getSchemeBase()
                    : "moyuyo://pay/return";
        }
        if (isPlaceholderKey(stripeSecretKey)) {
            String mockUrl = buildMockCheckoutUrl(request, order.getOrderNo(), isApp, schemeBase);
            log.warn("[payment] Stripe secret key is placeholder, returning MOCK checkout URL: {}", mockUrl);
            savePaymentRecord(order.getId(), "STRIPE", "MOCK-" + order.getOrderNo(), order.getPayAmount());
            // H5/测试密钥场景：返回 mock 跳转地址。
            // 使用 builder 而非全参构造，避免 CreatePaymentResponse 新增字段后构造函数参数不匹配。
            return CreatePaymentResponse.builder()
                    .paymentId("MOCK-" + order.getOrderNo())
                    .sessionUrl(mockUrl)
                    .payChannel("STRIPE")
                    .build();
        }
        // ========== 以下为真实 Stripe Checkout Session 逻辑 ==========
        // H1 修复：使用 Stripe.checkout.Session 替代手写 PaymentIntent。
        // Checkout Session 由 Stripe 托管支付页，支持 Card / Apple Pay / Google Pay / Alipay / Cash App。
        // 根据 payMethod 细分决定 payment_method_types（不传时 Stripe 按 Dashboard 自动开启）。
        // amount 单位为最小货币单位（USD = cents）
        long amountCents = order.getPayAmount().multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        if (amountCents <= 0) {
            throw new IllegalArgumentException("订单金额必须大于 0");
        }

        // APP 端优先走自定义 scheme，避免第三方 APP 付完回不来你的 APP：
        //   success: moyuyo://pay/return?status=success&orderNo=xxx
        //   cancel : moyuyo://pay/return?status=cancel&orderNo=xxx
        // 同时仍然保留 return.html 兜底路径（在 payAppBridge 的子 WebView 里仍能通过 URL 拦截关闭页面）
        // 【注意】isApp/schemeBase 已在方法开头 Mock 判断处声明并赋值，这里不能重复声明。
        String orderNoEnc = URLEncoder.encode(order.getOrderNo(), StandardCharsets.UTF_8);
        String successUrl;
        String cancelUrl;
        if (isApp && schemeBase != null) {
            successUrl = appendQuery(schemeBase, "status=success&orderNo=" + orderNoEnc);
            cancelUrl = appendQuery(schemeBase, "status=cancel&orderNo=" + orderNoEnc);
        } else {
            // H5/小程序：走中转页，由 return.html 通过 postMessage / localStorage 通知宿主
            String baseUrl = buildPublicBaseUrl(request.getReturnUrl());
            successUrl = baseUrl + "/payment/return.html?status=success&orderNo=" + orderNoEnc;
            cancelUrl = baseUrl + "/payment/return.html?status=cancel&orderNo=" + orderNoEnc;
        }

        // 根据 payMethod 细分映射 payment_method_types（Stripe Checkout SessionCreateParams 枚举）
        // 参考：https://docs.stripe.com/payments/payment-methods/payment-method-support
        SessionCreateParams.PaymentMethodType[] methodTypes = resolvePaymentMethodTypes(request.getPayMethod());

        try {
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(order.getOrderNo())
                    .putMetadata("order_no", order.getOrderNo())
                    .putMetadata("user_id", String.valueOf(order.getUserId()))
                    .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                            .putMetadata("order_no", order.getOrderNo())
                            .build())
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
                            .build());

            // 强制指定支付方式（若 payMethod 没传则走 Dashboard 自动启用列表）
            if (methodTypes != null && methodTypes.length > 0) {
                for (SessionCreateParams.PaymentMethodType type : methodTypes) {
                    paramsBuilder.addPaymentMethodType(type);
                }
            }
            SessionCreateParams params = paramsBuilder.build();

            Session session = Session.create(params);

            // 落库 PaymentEntity（transactionId 存 session.id）
            savePaymentRecord(order.getId(), "STRIPE", session.getId(), order.getPayAmount());

            log.info("Stripe checkout session created: sessionId={}, url={}, orderNo={}",
                    session.getId(), session.getUrl(), order.getOrderNo());

            return CreatePaymentResponse.builder()
                    .paymentId(session.getId())
                    .sessionUrl(session.getUrl())
                    .publishableKey(stripePublishableKey)
                    .payChannel("STRIPE")
                    .currencyCode(stripeCurrency.toUpperCase())
                    .countryCode("US")
                    // Apple Pay Merchant ID：给 iOS 原生插件或 Stripe React Native SDK 用
                    // 生产环境建议从 Nacos / application-prod.yml 动态注入
                    .applePayMerchantId(System.getProperty("payment.applepay.merchant-id",
                            "merchant.com.moyuyo.app"))
                    .build();
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
     * 在任意 URL 后追加 query，保留原 host/scheme（兼容 http(s) 和自定义 scheme，如 moyuyo://pay/return）。
     */
    private String appendQuery(String base, String query) {
        if (base == null || base.isEmpty()) return base;
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + query;
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

    /**
     * 根据前端传入的 payMethod 细分，映射为 Stripe Checkout 要求的 payment_method_types 数组。
     *
     * 对照关系：
     * APPLE_PAY -> card              （启用 card 后 Stripe 在支持的设备上动态出 Apple Pay 按钮）
     * GOOGLE_PAY-> card              （同上，动态出 Google Pay 按钮）
     * ALIPAY    -> alipay
     * CASH_APP  -> cashapp            美国 Cash App Pay
     * LINK      -> card + link
     * AFFIRM    -> affirm             美国 BNPL
     * AFTERPAY  -> afterpay_clearpay
     * PAYPAL    -> paypal             Stripe 侧 PayPal 收单
     * null/空   -> null               不传，Stripe 按 Dashboard 开通列表自动展示
     */
    private SessionCreateParams.PaymentMethodType[] resolvePaymentMethodTypes(String payMethod) {
        if (payMethod == null || payMethod.isBlank()) {
            return null;
        }
        String m = payMethod.toUpperCase().trim();
        switch (m) {
            case "APPLE_PAY":
            case "APPLEPAY":
            case "GOOGLE_PAY":
            case "GOOGLEPAY":
                // 启用 card 后 Stripe 会根据用户浏览器/设备自动出 Apple Pay / Google Pay / Samsung Pay 按钮
                return new SessionCreateParams.PaymentMethodType[]{
                        SessionCreateParams.PaymentMethodType.CARD
                };
            case "ALIPAY":
                return new SessionCreateParams.PaymentMethodType[]{
                        SessionCreateParams.PaymentMethodType.ALIPAY
                };
            case "CASH_APP":
            case "CASHAPP":
                return new SessionCreateParams.PaymentMethodType[]{
                        SessionCreateParams.PaymentMethodType.CASHAPP
                };
            case "LINK":
                return new SessionCreateParams.PaymentMethodType[]{
                        SessionCreateParams.PaymentMethodType.CARD,
                        SessionCreateParams.PaymentMethodType.LINK
                };
            case "AFFIRM":
                return new SessionCreateParams.PaymentMethodType[]{
                        SessionCreateParams.PaymentMethodType.AFFIRM
                };
            case "AFTERPAY":
            case "AFTERPAY_CLEARPAY":
                return new SessionCreateParams.PaymentMethodType[]{
                        SessionCreateParams.PaymentMethodType.AFTERPAY_CLEARPAY
                };
            case "PAYPAL":
                try {
                    return new SessionCreateParams.PaymentMethodType[]{
                            SessionCreateParams.PaymentMethodType.PAYPAL
                    };
                } catch (Exception ignore) {
                    return null;
                }
            default:
                log.warn("Unknown payMethod for Stripe: {}, fallback to dashboard auto", payMethod);
                return null;
        }
    }

    private CreatePaymentResponse createPayPalPayment(OrderEntity order, CreatePaymentRequest request) {
        // ========== Mock 兜底：PayPal 密钥是占位符时直接返回假 approvalUrl ==========
        boolean isApp = "APP".equalsIgnoreCase(request.getClientType());
        String schemeBase = null;
        if (isApp) {
            schemeBase = StringUtils.hasText(request.getSchemeBase())
                    ? request.getSchemeBase()
                    : "moyuyo://pay/return";
        }
        if (isPlaceholderKey(paypalClientId) || isPlaceholderKey(paypalClientSecret)) {
            String mockUrl = buildMockCheckoutUrl(request, order.getOrderNo(), isApp, schemeBase);
            log.warn("[payment] PayPal clientId/secret is placeholder, returning MOCK approval URL: {}", mockUrl);
            savePaymentRecord(order.getId(), "PAYPAL", "MOCK-" + order.getOrderNo(), order.getPayAmount());
            // H5/测试密钥场景：返回 mock 跳转地址。
            // 使用 builder 而非全参构造，避免 CreatePaymentResponse 新增字段后构造函数参数不匹配。
            return CreatePaymentResponse.builder()
                    .paymentId("MOCK-" + order.getOrderNo())
                    .approvalUrl(mockUrl)
                    .payChannel("PAYPAL")
                    .build();
        }
        // ========== 以下为真实 PayPal API 调用逻辑 ==========
        String accessToken = getPayPalAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        String baseUrl = "sandbox".equals(paypalMode)
                ? "https://api-m.sandbox.paypal.com"
                : "https://api-m.paypal.com";

        // APP 端优先走自定义 scheme，避免跳 PayPal/Venmo APP 付款后回不来；
        // H5 端仍然走中转页（postMessage / localStorage 转发结果）
        // 【注意】isApp/schemeBase 已在方法开头声明并赋值，此处不能重复声明。
        String successUrl;
        String cancelUrl;
        String orderNoEnc = URLEncoder.encode(order.getOrderNo(), StandardCharsets.UTF_8);
        if (isApp && schemeBase != null) {
            successUrl = appendQuery(schemeBase, "status=success&orderNo=" + orderNoEnc);
            cancelUrl = appendQuery(schemeBase, "status=cancel&orderNo=" + orderNoEnc);
        } else {
            successUrl = validateAndBuildReturnUrl(request.getReturnUrl(), "success", order.getOrderNo());
            cancelUrl = validateAndBuildReturnUrl(request.getReturnUrl(), "cancel", order.getOrderNo());
        }

        // PAYPAL 旗下两个子方式：PAYPAL（原有）、VENMO（PayPal 旗下 Venmo）
        // 同一渠道下通过 payMethod 区分 payment_source：
        //   VENMO -> payment_source.venmo   （美国主流 Venmo APP 支付）
        //   其他  -> payment_source.paypal  （默认）
        String payMethod = request.getPayMethod() == null ? "" : request.getPayMethod().toUpperCase().trim();
        boolean isVenmo = "VENMO".equals(payMethod);
        String paymentSourceBlock = isVenmo
                ? String.format("""
                  "venmo": {
                    "experience_context": {
                      "payment_method_preference": "IMMEDIATE_PAYMENT_REQUIRED",
                      "landing_page": "LOGIN",
                      "user_action": "PAY_NOW",
                      "return_url": "%s",
                      "cancel_url": "%s"
                    }
                  }""", successUrl, cancelUrl)
                : String.format("""
                  "paypal": {
                    "experience_context": {
                      "payment_method_preference": "IMMEDIATE_PAYMENT_REQUIRED",
                      "landing_page": "LOGIN",
                      "user_action": "PAY_NOW",
                      "return_url": "%s",
                      "cancel_url": "%s"
                    }
                  }""", successUrl, cancelUrl);

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
                    %s
                  }
                }""", order.getOrderNo(), order.getPayAmount().toPlainString(), paymentSourceBlock);

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
            // APP 原生通道：返回 paypalClientId + environment + currency + country，
            // 让 pay.vue 可以优先走 uni.requestPayment provider=paypal，
            // 避免 iOS WKWebView 白屏 & Android scheme 拦截丢失回跳。
            return CreatePaymentResponse.builder()
                    .paymentId(paypalOrderId)
                    .approvalUrl(approvalUrl)
                    .payChannel("PAYPAL")
                    .paypalClientId(isPlaceholderKey(paypalClientId) ? "" : paypalClientId)
                    .paypalEnvironment(paypalMode)
                    .currencyCode("USD")
                    .countryCode("US")
                    .build();
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

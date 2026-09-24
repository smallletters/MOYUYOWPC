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
import com.moyuyo.service.PrimeService;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:webhook:";
    /** webhook 短期并发 lock TTL：业务处理中防并发重复处理 */
    private static final long WEBHOOK_LOCK_TTL_MINUTES = 5;
    /** webhook 处理成功后写入的长期幂等 key TTL：渠道重投时直接跳过 */
    private static final long WEBHOOK_PROCESSED_TTL_HOURS = 24;
    /** webhook 异常重投熔断阈值：连续失败 N 次后写短期 PERM_FAIL key 阻断后续重投 */
    private static final int WEBHOOK_FAIL_THRESHOLD = 5;

    /**
     * 原子完成"写长期幂等 key + 删除短期 lock"两步操作。
     * 避免 set 与 delete 之间进程崩溃导致 5 分钟窗口期内事件被重投二次处理。
     * KEYS[1] = 长期 key（idempotent:webhook:{eventId}）
     * KEYS[2] = 短期 lock key（idempotent:webhook:{eventId}:lock）
     * ARGV[1] = 长期 key TTL 秒数
     */
    private static final RedisScript<Long> MARK_PROCESSED_ATOMIC = new DefaultRedisScript<>(
            "redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])\n" +
            "redis.call('DEL', KEYS[2])\n" +
            "return 1",
            Long.class);

    /**
     * 原子完成"INCR 失败计数 + 首次创建时设置 TTL"。
     * 避免 INCR 与 EXPIRE 之间崩溃导致计数 key 永驻。
     * KEYS[1] = 失败计数 key（idempotent:webhook:{eventId}:fail）
     * ARGV[1] = TTL 秒数（与 lock TTL 对齐，5 分钟内连续失败才算"反复失败"）
     * 返回：INCR 后的当前计数
     */
    private static final RedisScript<Long> INCR_FAIL_COUNT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
            "if c == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return c",
            Long.class);

    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final PrimeService primeService;
    /** 全局复用的 Jackson 实例，避免每次 new ObjectMapper 浪费资源 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 将 Object 转为 Map<String,Object>，用于 webhook payload 反序列化后的字段取值 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : null;
    }

    /** 将 Object 转为 Map<String,String>，用于 metadata 字段取值 */
    @SuppressWarnings("unchecked")
    private static Map<String, String> asStringMap(Object obj) {
        return obj instanceof Map ? (Map<String, String>) obj : null;
    }

    /** 将 Object 转为 List<Map<String,Object>>，用于 webhook payload 中 links 数组取值 */
    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, Object>> castLinks(Object obj) {
        return obj instanceof java.util.List ? (java.util.List<Map<String, Object>>) obj : null;
    }

    public PaymentServiceImpl(OrderService orderService,
                               PaymentMapper paymentMapper,
                               OrderMapper orderMapper,
                               @Qualifier("restTemplate") RestTemplate restTemplate,
                               StringRedisTemplate redisTemplate,
                               PrimeService primeService) {
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.primeService = primeService;
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

    @Value("${payment.paypal.allowed-origins:https://moyuyoshop.com,https://www.moyuyoshop.com}")
    private String paypalAllowedOrigins;

    /**
     * Bean 初始化时设置 Stripe SDK 全局 API key，避免每次调用重新设置。
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

        // 前置幂等：若同订单已有 PENDING 支付记录且未过期，复用其 transactionId 与跳转地址，
        // 避免用户重复点击"立即支付"产生多个有效 Session/Order，渠道回调混乱。
        CreatePaymentResponse reused = tryReusePendingPayment(order.getId(), request);
        if (reused != null) {
            return reused;
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
     * 前置幂等：查找该订单最近一笔 PENDING 支付记录，若渠道仍认可其 transactionId 则复用跳转地址。
     * <p>
     * 复用条件：transactionId 不为空且能成功 retrieve 出有效 url（H5 returnUrl 仍存在）。
     * 任一环节失败则返回 null，调用方按新流程走。
     */
    private CreatePaymentResponse tryReusePendingPayment(Long orderId, CreatePaymentRequest request) {
        // 必须按渠道过滤：同一订单上一次用 Stripe 创建的 PENDING 记录，
        // 这次切到 PayPal 再点"立即支付"时，不应复用 Stripe 的 session，避免渠道错乱。
        PaymentEntity pending = paymentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentEntity>()
                        .eq(e -> e.getOrderId(), orderId)
                        .eq(e -> e.getPayChannel(), request.getPayChannel())
                        .eq(e -> e.getStatus(), PENDING.name())
                        .orderByDesc(e -> e.getCreateTime())
                        .last("LIMIT 1"));
        if (pending == null) {
            return null;
        }
        String txId = pending.getTransactionId();
        if (txId == null || txId.isBlank() || txId.startsWith("MOCK-")) {
            // Mock 记录没有真实通道 session，不复用
            return null;
        }
        try {
            if ("STRIPE".equalsIgnoreCase(pending.getPayChannel())) {
                // 通过 Stripe SDK 拉取原 session（仅取 url，不重新创建）
                Session session = Session.retrieve(txId);
                if (session == null || session.getUrl() == null) {
                    return null;
                }
                log.info("Reuse existing Stripe session: orderId={}, sessionId={}", orderId, txId);
                return CreatePaymentResponse.builder()
                        .paymentId(session.getId())
                        .sessionUrl(session.getUrl())
                        .publishableKey(stripePublishableKey)
                        .payChannel("STRIPE")
                        .currencyCode(stripeCurrency.toUpperCase())
                        .countryCode("US")
                        .applePayMerchantId(System.getProperty("payment.applepay.merchant-id",
                                "merchant.com.moyuyo.app"))
                        .build();
            } else if ("PAYPAL".equalsIgnoreCase(pending.getPayChannel())) {
                // PayPal 没有轻量级 retrieve-approval 接口，这里直接复用本地 transactionId 作为 paymentId 返回，
                // 真正的 approvalUrl 由前端从上一次 createPayment 的结果中读取（前端 SDK 通常会缓存）。
                log.info("Reuse existing PayPal order: orderId={}, paypalOrderId={}", orderId, txId);
                return CreatePaymentResponse.builder()
                        .paymentId(txId)
                        .payChannel("PAYPAL")
                        .paypalClientId(isPlaceholderKey(paypalClientId) ? "" : paypalClientId)
                        .paypalEnvironment(paypalMode)
                        .currencyCode("USD")
                        .countryCode("US")
                        .build();
            }
            return null;
        } catch (Exception e) {
            // 复用失败（session 已过期/被取消/网络异常），让调用方走新建流程
            log.warn("Reuse pending payment failed, fallback to create new: orderId={}, txId={}, error={}",
                    orderId, txId, e.getMessage());
            return null;
        }
    }

    /**
     * 判断 Stripe/PayPal 密钥是否是占位符或未配置。
     * 如果未配置，进入 mock 模式返回假的 Checkout URL，便于本地调试和 APP 打包测试 UI 流程。
     * 命中条件：null / 空白 / 包含 "placeholder" 或 "replace_with"（.env 默认占位）/
     * 以 "sk_test_placeholder" 等占位格式开头。
     */
    private boolean isPlaceholderKey(String key) {
        if (!StringUtils.hasText(key)) return true;
        String k = key.trim();
        String lower = k.toLowerCase();
        if (lower.contains("placeholder")) return true;
        // 兼容 .env 默认占位文案 REPLACE_WITH_XXX，避免前端调真实 PayPal 接口 401
        if (lower.contains("replace_with")) return true;
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
            saveOrReusePaymentRecord(order.getId(), "STRIPE", "MOCK-" + order.getOrderNo(), order.getPayAmount());
            // H5/测试密钥场景：返回 mock 跳转地址。
            // 使用 builder 而非全参构造，避免 CreatePaymentResponse 新增字段后构造函数参数不匹配。
            return CreatePaymentResponse.builder()
                    .paymentId("MOCK-" + order.getOrderNo())
                    .sessionUrl(mockUrl)
                    .payChannel("STRIPE")
                    .build();
        }
        // ========== 以下为真实 Stripe Checkout Session 逻辑 ==========
        // 使用 Stripe.checkout.Session 替代手写 PaymentIntent。
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

            // 落库或复用 PaymentEntity（transactionId 存 session.id）
            String actualTransactionId = saveOrReusePaymentRecord(order.getId(), "STRIPE", session.getId(), order.getPayAmount());

            log.info("Stripe checkout session created: sessionId={}, url={}, orderNo={}",
                    session.getId(), session.getUrl(), order.getOrderNo());

            // 用真正落库的 transactionId（复用场景下可能与 session.getId() 不同）
            return CreatePaymentResponse.builder()
                    .paymentId(actualTransactionId != null ? actualTransactionId : session.getId())
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
        return "https://moyuyoshop.com";
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
            saveOrReusePaymentRecord(order.getId(), "PAYPAL", "MOCK-" + order.getOrderNo(), order.getPayAmount());
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
        // 使用 Map + ObjectMapper 序列化请求体，避免 String.format 拼接导致的 JSON 注入
        Map<String, Object> experienceContext = new java.util.LinkedHashMap<>();
        experienceContext.put("payment_method_preference", "IMMEDIATE_PAYMENT_REQUIRED");
        experienceContext.put("landing_page", "LOGIN");
        experienceContext.put("user_action", "PAY_NOW");
        experienceContext.put("return_url", successUrl);
        experienceContext.put("cancel_url", cancelUrl);

        Map<String, Object> paymentSourceNode = new java.util.LinkedHashMap<>();
        paymentSourceNode.put(isVenmo ? "venmo" : "paypal", experienceContext);

        Map<String, Object> amount = new java.util.LinkedHashMap<>();
        amount.put("currency_code", "USD");
        amount.put("value", order.getPayAmount().toPlainString());

        Map<String, Object> purchaseUnit = new java.util.LinkedHashMap<>();
        purchaseUnit.put("reference_id", order.getOrderNo());
        purchaseUnit.put("description", "MOYUYO Order");
        purchaseUnit.put("amount", amount);

        Map<String, Object> orderBody = new java.util.LinkedHashMap<>();
        orderBody.put("intent", "CAPTURE");
        orderBody.put("purchase_units", java.util.Collections.singletonList(purchaseUnit));
        orderBody.put("payment_source", paymentSourceNode);

        String orderJson;
        try {
            orderJson = objectMapper.writeValueAsString(orderBody);
        } catch (Exception e) {
            log.error("PayPal order body 序列化失败", e);
            throw new RuntimeException("PayPal payment service unavailable");
        }

        HttpEntity<String> entity = new HttpEntity<>(orderJson, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/v2/checkout/orders",
                    HttpMethod.POST,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> result = response.getBody();
            if (result == null) {
                throw new RuntimeException("PayPal order creation failed");
            }

            String paypalOrderId = (String) result.get("id");

            String approvalUrl = null;
            java.util.List<Map<String, Object>> links = castLinks(result.get("links"));
            if (links != null) {
                for (Map<String, Object> link : links) {
                    if ("payer-action".equals(link.get("rel"))) {
                        approvalUrl = (String) link.get("href");
                        break;
                    }
                }
            }

            String actualPaypalOrderId = saveOrReusePaymentRecord(order.getId(), "PAYPAL", paypalOrderId, order.getPayAmount());

            log.info("PayPal order created: paypalOrderId={}, orderNo={}", actualPaypalOrderId, order.getOrderNo());
            // APP 原生通道：返回 paypalClientId + environment + currency + country，
            // 让 pay.vue 可以优先走 uni.requestPayment provider=paypal，
            // 避免 iOS WKWebView 白屏 & Android scheme 拦截丢失回跳。
            return CreatePaymentResponse.builder()
                    .paymentId(actualPaypalOrderId != null ? actualPaypalOrderId : paypalOrderId)
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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/v1/oauth2/token",
                    HttpMethod.POST,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
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
            // 用 Stripe SDK 的 Webhook.constructEvent 验签（包含时间戳容差校验）
            signatureValid = verifyStripeSignature(payload, headers.get("stripe-signature"));
        } else if ("PAYPAL".equalsIgnoreCase(payChannel)) {
            signatureValid = verifyPayPalSignature(payload, headers);
        } else {
            // 未知渠道必须抛 RuntimeException 让 controller 返 500，
            // 否则渠道不重投，事件被静默丢弃。
            // 攻击者也无法伪造合法渠道重投，重投仍会被验签/分支判断拒绝，
            // 不会进入 handler 执行业务，安全可控。
            // 同时运维可从 ERROR 日志/告警感知调用方传错渠道。
            log.error("Unknown webhook channel: {}", payChannel);
            throw new RuntimeException("Unknown webhook channel: " + payChannel);
        }
        // 重要：签名失败时必须抛 RuntimeException 让 controller 返 500。
        // 不能 return 让 controller 返 200，否则渠道认为已成功不再重投，
        // 若 webhook secret 配错，所有事件都会被静默丢弃，运营永远发现不了。
        // 抛 RuntimeException → controller 返 500 → 渠道按自己间隔重投，
        // 运维可从告警/日志感知 secret 配置错误。
        // 同时攻击者无法伪造我们的 webhook secret 重投，重投也仍会被签名校验拒绝，
        // 不会进入 handler 执行业务，安全可控。
        if (!signatureValid) {
            log.error("Webhook 签名校验失败: channel={}", payChannel);
            throw new RuntimeException("Webhook signature verification failed, channel=" + payChannel);
        }

        // 幂等策略分两步：
        // 1) 处理前抢短期 lock（5 分钟）防并发重复处理
        // 2) 处理成功后由具体 handler 写长期 key（24 小时）让渠道重投跳过
        // 业务失败时释放 lock 让渠道重投生效（不会被错误地丢弃）
        //
        // eventId 解析失败时必须让渠道重投（不能 return 让 controller 返 200）。
        // 若 return + 返 200，渠道认为已成功处理不再重投，但本地根本没处理业务，
        // 用户付了钱但订单永远不会更新 —— 最严重的丢钱路径。
        // 改为抛 RuntimeException：controller 返 500 触发渠道重投，业务进入完整幂等流程。
        String eventId = extractEventId(payload);
        if (eventId == null) {
            log.error("Webhook payload 缺少事件 ID，无法幂等，丢弃: channel={}", payChannel);
            throw new RuntimeException("Webhook payload missing event id, channel=" + payChannel);
        }

        String idempotentKey = IDEMPOTENT_KEY_PREFIX + eventId;
        String lockKey = idempotentKey + ":lock";

        // 长期 key 已存在，说明此前已成功处理过，直接跳过
        // hasKey 抛异常时不能继续往下走（无法判断是否已处理，
        // 继续走可能与已成功的业务重复触发副作用）。
        // 抛 RuntimeException 让 controller 返 500，渠道按自己间隔重投，Redis 恢复后能正常处理。
        Boolean alreadyProcessed;
        try {
            alreadyProcessed = redisTemplate.hasKey(idempotentKey);
        } catch (Exception e) {
            log.error("[REDIS_HASKEY_FAILED] 查询幂等 key 时 Redis 异常: eventId={}, channel={}",
                    eventId, payChannel, e);
            throw new RuntimeException("Failed to check webhook idempotency, eventId=" + eventId, e);
        }
        if (alreadyProcessed == null) {
            // Redis 返回 null（极端配置异常），无法判断是否已处理，继续往下走可能与已成功业务并发。
            // 抛 RuntimeException 让渠道重投，Redis 恢复后能正常处理。
            log.error("[REDIS_HASKEY_NULL] hasKey 返回 null: eventId={}, channel={}", eventId, payChannel);
            throw new RuntimeException("Webhook hasKey returned null, eventId=" + eventId);
        }
        if (Boolean.TRUE.equals(alreadyProcessed)) {
            log.info("Webhook 已处理，幂等跳过: eventId={}, channel={}", eventId, payChannel);
            return;
        }
        // 抢短期并发 lock：setIfAbsent 保证只有一个节点进入处理
        // setIfAbsent 抛异常时 acquired 未初始化，不能继续往下走，
        // 否则可能与另一节点并发处理同一事件，导致 payCallback 副作用重复触发。
        // 返回 null 同样按未抢到 lock 处理（极端配置异常）。
        // 统一抛 RuntimeException 让 controller 返 500 + 渠道重投。
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", WEBHOOK_LOCK_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("[REDIS_LOCK_FAILED] 抢短期并发 lock 时 Redis 异常: eventId={}, channel={}",
                    eventId, payChannel, e);
            throw new RuntimeException("Failed to acquire webhook lock, eventId=" + eventId, e);
        }
        if (acquired == null) {
            // Redis 返回 null（配置异常等极端情况），按未抢到 lock 处理
            log.error("[REDIS_LOCK_NULL] setIfAbsent 返回 null: eventId={}, channel={}", eventId, payChannel);
            throw new RuntimeException("Webhook lock acquired returned null, eventId=" + eventId);
        }
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Webhook 正在被另一节点处理，跳过: eventId={}, channel={}", eventId, payChannel);
            return;
        }

        boolean processed = false;
        try {
            if ("STRIPE".equalsIgnoreCase(payChannel)) {
                processed = handleStripeWebhook(payload);
            } else if ("PAYPAL".equalsIgnoreCase(payChannel)) {
                processed = handlePayPalWebhook(payload);
            } else {
                // 理论上前面渠道校验已过滤掉未知渠道，
                // 走到这里说明 payChannel 被外部异常修改，应让渠道重投以保留运维可观测性。
                log.error("Unsupported webhook channel after signature check: {}", payChannel);
                throw new RuntimeException("Unsupported webhook channel: " + payChannel);
            }
        } catch (Throwable t) {
            // handler 内业务调用（payCallback / primeService.handleCheckoutCompleted 等）抛异常时必须冒泡。
            // 释放 lock 让渠道重投能进入业务；setRollbackOnly + rethrow 让 controller 返 500 + 事务回滚。
            // 若不冒泡（return），Spring 不知道发生了异常，事务不会回滚，可能部分提交；
            // 同时渠道不重投，造成数据不一致。
            releaseWebhookLock(eventId);
            if (t instanceof Error) {
                log.error("Webhook 处理 Error 异常（不可恢复）: channel={}", payChannel, t);
            } else {
                log.error("Webhook 处理异常: channel={}", payChannel, t);
            }
            // 重投熔断：5 分钟内连续失败 WEBHOOK_FAIL_THRESHOLD 次视为上游/环境长期不可用，
            // 写短期 PERM_FAIL key 阻断后续重投，避免上游异常期间无限重投风暴。
            // 计数失败时不影响重投逻辑（乐观放过：单次计数缺失不影响业务自愈路径）。
            try {
                    Long count = redisTemplate.execute(INCR_FAIL_COUNT,
                            java.util.Collections.singletonList(IDEMPOTENT_KEY_PREFIX + eventId + ":fail"),
                            String.valueOf(WEBHOOK_LOCK_TTL_MINUTES * 60));
                    if (count != null && count >= WEBHOOK_FAIL_THRESHOLD) {
                        log.error("[WEBHOOK_FAIL_BREAKER] 连续失败达阈值，写 PERM_FAIL 阻断重投: "
                                        + "eventId={}, channel={}, failCount={}, threshold={}",
                                eventId, payChannel, count, WEBHOOK_FAIL_THRESHOLD);
                        markPermanentFailure(eventId, "consecutive_failures_breached_threshold");
                    }
                } catch (Exception counterEx) {
                    log.warn("[FAIL_COUNT_INCR_FAILED] 失败计数异常，不影响本次重投: eventId={}, channel={}",
                            eventId, payChannel, counterEx);
                }
            // 防御：显式标记事务回滚，避免未来 OperationLog / 其他切面改动后
            // 异常被吞掉导致事务无法触发回滚。即便 rethrow 后切面拦截了异常，
            // 这里已经 markRollbackOnly，事务仍会回滚（虽然 controller 可能返 200）。
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();
            // rethrow 让 @Transactional 感知异常触发回滚，controller 返 500 触发渠道重投
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new RuntimeException(t);
            }
        }

        // 仅当 handler 明确处理成功才写长期幂等 key（避免业务失败被"成功吞掉"）
        if (processed) {
            // 清理失败计数：业务已成功，此前累积的失败计数应清零，
            // 避免 24h 后渠道意外重投同一 eventId 时误判为"连续失败"。
            try {
                redisTemplate.delete(IDEMPOTENT_KEY_PREFIX + eventId + ":fail");
            } catch (Exception ignore) {
                // 计数清理失败不影响主流程，TTL 自然过期即可
            }
            // 写长期 key 与删 lock 用 Lua 原子完成
            // Redis 写失败时改为"乐观放过"而不是抛 RuntimeException。
            // 原因：此时 payCallback 已成功执行（外部副作用：积分扣减/Woo 同步/通知/MQ 已发出），
            // 若抛 RuntimeException 触发事务回滚，DB 内的 PaymentEntity SUCCESS 记录会被回滚成无，
            // 下次重投时 payCallback 抢占成功（claimed > 0），所有外部副作用会**重复触发**，
            // 用户被通知两次、积分被扣两次。这比幂等丢失更糟。
            // 乐观放过时必须主动 releaseWebhookLock，不能依赖 5 分钟 TTL 自然过期。
            // 否则 lock 残留期间渠道重投会被 setIfAbsent 拦下（业务被静默跳过 5 分钟）。
            // 主动释放 lock 后渠道重投能进入，payCallback 抢占会 claimed == 0 走对账分支，
            // 不会重复外部副作用。重投幂等保护由 payCallback 抢占式更新承担。
            try {
                redisTemplate.execute(MARK_PROCESSED_ATOMIC,
                        java.util.Arrays.asList(idempotentKey, lockKey),
                        String.valueOf(WEBHOOK_PROCESSED_TTL_HOURS * 3600L));
            } catch (Exception e) {
                log.error("[IDEMPOTENCY_PERSIST_FAILED] Lua 写入长期幂等 key 失败，业务已提交，"
                                + "接受幂等丢失，主动释放 lock 防重投被静默跳过: eventId={}, channel={}",
                        eventId, payChannel, e);
                // 主动释放 lock，让渠道重投能进入业务。
                // 业务已提交，payCallback 抢占会 claimed == 0 走对账分支，无副作用重复。
                releaseWebhookLock(eventId);
            }
        } else {
            // handler 返回 false 表示"永久性失败"（如缺 orderNo / paypalOrderId），
            // 写短期幂等 key（1 小时）覆盖 idempotentKey 位置，避免渠道无限重投风暴。
            // 1 小时后渠道如重投，handler 会再次 return false，再次续期短期 key。
            // markPermanentFailure 写的是 idempotentKey（不是 lockKey），与 lock 是独立 key。
            // 这里仍然 releaseWebhookLock 主动释放 lock，避免 5 分钟内 lock 残留导致重投被 setIfAbsent 拦下。
            // 因为 hasKey(idempotentKey) 已命中 PERM_FAIL，重投会直接 return，不会进入 handler 也不需要 lock。
            log.error("[PERMANENT_FAILURE] handler 无法处理事件，写短期幂等 key 防重投风暴: eventId={}, channel={}",
                    eventId, payChannel);
            markPermanentFailure(eventId, "handler returned false");
            releaseWebhookLock(eventId);
        }
    }

    /**
     * 释放 webhook 短期并发 lock，让渠道重投能立即生效。
     * lock 自然 TTL 到期也会失效，此方法仅用于主动释放以缩短等待。
     */
    private void releaseWebhookLock(String eventId) {
        if (eventId == null) return;
        try {
            redisTemplate.delete(IDEMPOTENT_KEY_PREFIX + eventId + ":lock");
        } catch (Exception e) {
            log.warn("Failed to release webhook lock: eventId={}, error={}", eventId, e.getMessage());
        }
    }

    /**
     * 写入短期幂等 key（1 小时），用于"永久性失败"场景（缺少 orderNo / paypalOrderId 等不可修复问题）。
     * <p>
     * 与长期幂等 key 不同：这里用较短 TTL 让重投仍有间隔地持续失败，便于监控告警，
     * 又避免 return false 导致的无限重投风暴。
     * <p>
     * 同时保留短期 lock 以便：1) 多节点不并发处理同一事件；2) 让运维看到 lock 仍在等待处理。
     */
    private void markPermanentFailure(String eventId, String reason) {
        if (eventId == null) return;
        try {
            // 长期 key 写为 "PERM_FAIL:reason" 标记永久失败，TTL 1 小时
            String value = "PERM_FAIL:" + (reason == null ? "unknown" : reason);
            redisTemplate.opsForValue().set(IDEMPOTENT_KEY_PREFIX + eventId, value, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to mark permanent failure: eventId={}, reason={}, error={}",
                    eventId, reason, e.getMessage());
        }
    }

    /**
     * 透传 payCallback 异常。
     * <p>
     * handler 内部不能 catch 后 return true，否则事务已部分提交但幂等写成功，
     * 渠道重投时会重复执行业务（重复落账/重复通知）。此方法不做任何包装，
     * 仅作为注释提示：payCallback 抛出的 RuntimeException 必须穿透 handler 冒泡到上层 catch。
     */
    private void safePayCallback(String orderNo, String payChannel, String transactionId) {
        orderService.payCallback(orderNo, payChannel, transactionId);
    }

    /**
     * 用 Stripe SDK 官方 Webhook.constructEvent 验签，
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

        // 构造验签请求体：使用 ObjectMapper 序列化字段，避免 String.format 拼接导致的 JSON 注入
        // webhook_event 是原始 payload（PayPal 要求原样嵌入），其它字段从 header 取出
        Map<String, Object> webhookEvent;
        try {
            webhookEvent = objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception parseEx) {
            log.error("PayPal webhook payload 解析失败", parseEx);
            return false;
        }
        Map<String, Object> verifyBodyMap = new java.util.LinkedHashMap<>();
        verifyBodyMap.put("transmission_id", transmissionId);
        verifyBodyMap.put("transmission_time", transmissionTime);
        verifyBodyMap.put("cert_url", certUrl);
        verifyBodyMap.put("auth_algo", authAlgo != null ? authAlgo : "SHA256withRSA");
        verifyBodyMap.put("transmission_sig", transmissionSig);
        verifyBodyMap.put("webhook_id", paypalWebhookId);
        verifyBodyMap.put("webhook_event", webhookEvent);
        String verifyBody;
        try {
            verifyBody = objectMapper.writeValueAsString(verifyBodyMap);
        } catch (Exception e) {
            log.error("PayPal verify body 序列化失败", e);
            return false;
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(verifyBody, httpHeaders);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/v1/notifications/verify-webhook-signature",
                    HttpMethod.POST,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
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
        String defaultUrl = "https://moyuyoshop.com/payment/return.html?status=" + suffix
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
            Map<String, Object> event = objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return (String) event.get("id");
        } catch (Exception e) {
            log.warn("提取 webhook 事件 ID 失败", e);
            return null;
        }
    }

    /**
     * 处理 Stripe webhook。
     * <p>
     * handler 顶部的 try/catch 范围仅覆盖 JSON 解析阶段，业务调用
     * （payCallback / primeService.handleCheckoutCompleted）抛出时必须冒泡到外层
     * handleWebhook 的 catch (Throwable)，由 controller 返 500 触发渠道重投 + 事务回滚。
     * 否则事务可能部分提交 + 幂等写成功 = 用户付了钱但订单没更新且永不重投。
     *
     * @return true 表示正常处理完成（无论订单是否找到，都算"已消费"），false 表示业务无法处理需重投。
     * @throws RuntimeException 业务执行异常（DB/网络等可重试错误），由上层 catch 释放 lock
     */
    private boolean handleStripeWebhook(String payload) {
        Map<String, Object> event;
        try {
            event = objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // JSON 解析失败：永久性错误，重投还是同样错。视为已消费，记告警让运维介入
            log.error("Failed to parse Stripe webhook payload (permanent failure)", e);
            return true;
        }
        String type = (String) event.get("type");

        // 优先处理 Checkout Session 事件，与 createStripePayment 中 Stripe.checkout.Session 一致
        if ("checkout.session.completed".equals(type)) {
            Map<String, Object> data = asMap(event.get("data"));
            Map<String, Object> object = asMap(data != null ? data.get("object") : null);
            if (object == null) return true;
            String sessionId = (String) object.get("id");
            Object metadataObj = object.get("metadata");
            Map<String, String> metadata = asStringMap(metadataObj);

            // Prime 会员订阅支付：metadata.biz=prime，由 PrimeService 激活订阅
            if (metadata != null && "prime".equals(metadata.get("biz"))) {
                // Prime handler 失败必须冒泡（不再 catch Exception）。
                // 原实现 Prime handler 内部 catch 后只记日志，被本 handler 当作"成功"返回 true，
                // 长期幂等 key 写成功后渠道永远不重投，导致用户付了钱但订阅没开通。
                handlePrimeCheckoutCompleted(sessionId, metadata);
                return true;
            }

            String orderNo = metadata != null ? metadata.get("order_no") : null;
            // 兜底：取 client_reference_id（创建 session 时设置的 orderNo）
            if (orderNo == null) {
                orderNo = (String) object.get("client_reference_id");
            }
            if (orderNo == null) {
                log.warn("Stripe checkout.session.completed missing orderNo, sessionId={}", sessionId);
                // 缺少 orderNo 视为业务无法处理，释放 lock 让渠道重投
                return false;
            }
            // payCallback 异常必须抛出，由上层 catch 释放 lock 让渠道重投
            safePayCallback(orderNo, "STRIPE", sessionId);
            log.info("Stripe checkout.session.completed: orderNo={}, sessionId={}", orderNo, sessionId);
            return true;
        }

        if ("checkout.session.expired".equals(type)) {
            Map<String, Object> data = asMap(event.get("data"));
            Map<String, Object> object = asMap(data != null ? data.get("object") : null);
            if (object == null) return true;
            String sessionId = (String) object.get("id");
            markPaymentFailedByTransactionId(sessionId, "checkout_session_expired");
            log.info("Stripe checkout.session.expired: sessionId={}", sessionId);
            return true;
        }

        // 兼容老链路：直接用 PaymentIntent API 也能走通
        if ("payment_intent.succeeded".equals(type)) {
            Map<String, Object> data = asMap(event.get("data"));
            Map<String, Object> object = asMap(data != null ? data.get("object") : null);
            if (object == null) return true;
            String paymentIntentId = (String) object.get("id");
            Object metadataObj = object.get("metadata");
            Map<String, String> metadata = asStringMap(metadataObj);
            String orderNo = resolveOrderNoByTransactionId(paymentIntentId,
                    metadata != null ? metadata.get("order_no") : null);
            if (orderNo != null) {
                // payCallback 异常必须抛出
                safePayCallback(orderNo, "STRIPE", paymentIntentId);
                log.info("Stripe webhook processed: orderNo={}, paymentIntentId={}", orderNo, paymentIntentId);
            } else {
                // 反查不到订单号（本地 PaymentEntity 还没落库，或订单已被清理）。
                // 返回 false 让渠道重投：本地 PaymentEntity 落库后重投可命中。
                // 不能返回 true 写长期幂等 key，否则这笔钱永远丢失。
                log.warn("Stripe webhook 找不到订单，等待重投: paymentIntentId={}", paymentIntentId);
                return false;
            }
            return true;
        }

        if ("payment_intent.payment_failed".equals(type)) {
            Map<String, Object> data = asMap(event.get("data"));
            Map<String, Object> object = asMap(data != null ? data.get("object") : null);
            if (object == null) return true;
            String paymentIntentId = (String) object.get("id");
            String lastError = extractStripeErrorMessage(object);
            markPaymentFailedByTransactionId(paymentIntentId, lastError);
            log.warn("Stripe payment failed: paymentIntentId={}, reason={}", paymentIntentId, lastError);
            return true;
        }

        if ("payment_intent.canceled".equals(type)) {
            Map<String, Object> data = asMap(event.get("data"));
            Map<String, Object> object = asMap(data != null ? data.get("object") : null);
            if (object == null) return true;
            String paymentIntentId = (String) object.get("id");
            markPaymentFailedByTransactionId(paymentIntentId, "canceled_by_stripe");
            log.info("Stripe payment canceled: paymentIntentId={}", paymentIntentId);
            return true;
        }

        log.debug("Ignoring Stripe webhook event: {}", type);
        return true;
    }

    /**
     * 处理 Prime 订阅支付成功：从 Checkout Session metadata 还原 userId/套餐并激活。
     * <p>
     * 本方法不吞任何异常。运行期异常与"参数缺失/格式非法"统一抛 IllegalStateException，
     * 由上层 handleWebhook 的 catch (Throwable) 释放 lock + rethrow 让 controller 返 500，
     * 触发 Stripe 重投。多次重投仍失败则由 markPermanentFailure 短期 key 防无限重投。
     *
     * @throws IllegalStateException 参数缺失/格式非法/PrimeService 激活失败时抛出
     */
    private void handlePrimeCheckoutCompleted(String sessionId, Map<String, String> metadata) {
        String uidRaw = metadata.get("user_id");
        String planCode = metadata.get("plan_code");
        if (uidRaw == null || planCode == null) {
            // 业务参数缺失：抛 RuntimeException 让 handleStripeWebhook 透传到 handleWebhook 的 catch (Throwable)，
            // 释放 lock 让渠道重投（不要立即写 24h 长期幂等 key，否则用户付了钱但订阅永远不开通）。
            // 多次重投仍失败时，由 markPermanentFailure 写短期 key 防止无限重投风暴。
            log.error("Prime checkout.completed 缺少 user_id/plan_code（重投+人工补单）: sessionId={}", sessionId);
            throw new IllegalStateException("Prime checkout metadata missing user_id/plan_code, sessionId=" + sessionId);
        }
        Long userId;
        try {
            userId = Long.valueOf(uidRaw);
        } catch (NumberFormatException nfe) {
            // 用户 ID 格式非法同样抛 RuntimeException，让渠道重投并由人工补单兜底
            log.error("Prime checkout.completed user_id 格式非法: uidRaw={}, sessionId={}", uidRaw, sessionId);
            throw new IllegalStateException("Prime checkout user_id format invalid: " + uidRaw, nfe);
        }
        // 运行期异常（DB/网络）抛给上层：handler 整体返回 false，渠道重投
        primeService.handleCheckoutCompleted(userId, planCode, "STRIPE", sessionId);
        log.info("Prime checkout.session.completed: userId={}, planCode={}, sessionId={}",
                userId, planCode, sessionId);
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
    @SuppressWarnings("null")
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
            Object msg = asMap(lastErr).get("message");
            if (msg != null) {
                return msg.toString();
            }
        }
        return "payment_failed";
    }

    /**
     * 把对应 PaymentEntity 标记为 FAILED；如果还没创建 PaymentEntity（用户未走到 createPayment）则忽略。
     * 不强行取消订单：让用户在前端看到 PENDING_PAY 时可重新发起支付，由 OrderTimeoutCancelJob 兜底取消。
     * <p>
     * mark FAILED 是次要业务，不应让 webhook handler 抛出异常导致 controller 返 500。
     * 失败时仅 log warn，由下次渠道重投或运营补偿处理。
     */
    @SuppressWarnings("null")
    private void markPaymentFailedByTransactionId(String transactionId, String reason) {
        if (transactionId == null) {
            return;
        }
        try {
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
        } catch (Exception e) {
            // 次要业务失败不应冒泡影响主流程，log warn 让运营介入
            log.warn("[MARK_PAYMENT_FAILED] 标记 PaymentEntity FAILED 失败: transactionId={}, reason={}",
                    transactionId, reason, e);
        }
    }

    @SuppressWarnings("null")
    /**
     * 处理 PayPal webhook。
     * <p>
     * handler 顶部的 try/catch 范围仅覆盖 JSON 解析阶段，
     * 业务调用（safePayCallback）抛出时必须冒泡到外层 catch (Throwable)，
     * 由 controller 返 500 触发渠道重投 + 事务回滚。
     *
     * @return true 表示正常处理完成（无论订单是否找到，都算"已消费"），false 表示业务无法处理需重投。
     * @throws RuntimeException 业务执行异常（DB/网络等可重试错误），由上层 catch 释放 lock
     */
    private boolean handlePayPalWebhook(String payload) {
        Map<String, Object> event;
        try {
            event = objectMapper.readValue(payload, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // JSON 解析失败：永久性错误，重投还是同样错。视为已消费，记告警让运维介入
            log.error("Failed to parse PayPal webhook payload (permanent failure)", e);
            return true;
        }
        String eventType = (String) event.get("event_type");

        if (!"CHECKOUT.ORDER.APPROVED".equals(eventType) && !"PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
            log.debug("Ignoring PayPal webhook event: {}", eventType);
            return true;
        }

        // 资金安全：ORDER.APPROVED 只代表买家在 PayPal/Venmo 页授权，款项未实际捕获。
        // 若此时调用 payCallback 推进本地订单，capture 阶段一旦失败（风控/3DS/余额不足等），
        // 订单会被错误地标记为已支付但钱未到账。仅记日志，等待 CAPTURE.COMPLETED 真正落账。
        if ("CHECKOUT.ORDER.APPROVED".equals(eventType)) {
            log.info("PayPal checkout order approved (funds not yet captured), waiting for CAPTURE.COMPLETED: resourceId={}",
                    asMap(event.get("resource")) != null ? asMap(event.get("resource")).get("id") : null);
            return true;
        }

        Map<String, Object> resource = asMap(event.get("resource"));
        if (resource == null) return true;

        // PAYMENT.CAPTURE.COMPLETED: resource 是 capture 对象，真正的 orderId
        // 需从 supplementary_data.related_ids.order_id 取。
        String paypalOrderId = null;
        if ("PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
            Map<String, Object> supplementaryData = asMap(resource.get("supplementary_data"));
            Map<String, Object> relatedIds = supplementaryData != null ? asMap(supplementaryData.get("related_ids")) : null;
            if (relatedIds != null && relatedIds.get("order_id") instanceof String) {
                paypalOrderId = (String) relatedIds.get("order_id");
            }
        }
        // 兜底：若 CAPTURE.COMPLETED 没从 supplementary_data 拿到 orderId，尝试从 links 中反推
        if (paypalOrderId == null) {
            java.util.List<Map<String, Object>> links = castLinks(resource.get("links"));
            if (links != null) {
                for (Map<String, Object> link : links) {
                    String href = (String) link.get("href");
                    // 使用常量字符串长度，避免硬编码偏移量出错
                    final String orderPath = "/v2/checkout/orders/";
                    if (href != null && href.contains(orderPath)) {
                        String extracted = href.substring(href.indexOf(orderPath) + orderPath.length());
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

        if (paypalOrderId == null) {
            // 解析不出 orderId 视为业务无法处理，释放 lock 让渠道重投
            log.warn("PayPal webhook 无法解析 orderId: eventType={}, resourceKeys={}",
                    eventType, resource.keySet());
            return false;
        }

        PaymentEntity payment = paymentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentEntity>()
                        .eq(PaymentEntity::getTransactionId, paypalOrderId));
        if (payment != null) {
            OrderEntity order = orderMapper.selectById(payment.getOrderId());
            if (order != null) {
                String orderNo = order.getOrderNo();
                // payCallback 异常必须抛出（见 Stripe handler 注释）
                safePayCallback(orderNo, "PAYPAL", paypalOrderId);
                log.info("PayPal webhook processed: orderNo={}, paypalOrderId={}, eventType={}",
                        orderNo, paypalOrderId, eventType);
                return true;
            }
            // 极端异常：本地 PaymentEntity 存在但 Order 已被清理。
            // 不能返回 true 写长期幂等 key（钱到账了订单却没更新，人工无法重投）。
            // 返回 false 让渠道重投直到对账/人工补单恢复一致性。
            log.error("PayPal webhook 找到支付记录但订单已不存在，等待重投: paypalOrderId={}", paypalOrderId);
            return false;
        }
        // 找不到支付记录：可能是本地 PaymentEntity 还未落库（极端时序），
        // 或订单已被清理。返回 false 让渠道重投：PaymentEntity 落库后重投可命中，
        // 不能返回 true 写长期幂等 key，否则这笔钱永远丢失。
        log.warn("PayPal webhook 找不到对应支付记录，等待重投: paypalOrderId={}, eventType={}",
                paypalOrderId, eventType);
        return false;
    }

    /**
     * 落库或复用已有的支付记录。
     * <p>
     * 业务约束：同一订单同一渠道同一交易(transactionId)重复创建时必须复用，
     * 否则后续回调按 transactionId 反查会随机命中旧记录导致订单状态更新错乱。
     * <p>
     * 命中策略：
     * 1) 精确匹配 transactionId：唯一允许的复用路径，避免在 Stripe/PayPal 后台产生孤儿 session/order；
     * 2) 没有命中则新建一条 PENDING 记录，transactionId 由调用方传入（新 session/order 的 ID）。
     * <p>
     * 注意：本方法不再"按同订单同渠道 PENDING 复用旧 transactionId"——
     * 调用方进入本方法时已经说明 tryReusePendingPayment 没找到可复用项；
     * 此处若仍以旧 transactionId 覆盖新 session，会造成渠道后台孤儿 session 与本地数据库不一致。
     *
     * @return 实际使用的 transactionId（复用旧值或新生成值）
     */
    private String saveOrReusePaymentRecord(Long orderId, String payChannel, String transactionId,
                                            java.math.BigDecimal amount) {
        if (transactionId != null) {
            PaymentEntity exist = paymentMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentEntity>()
                            .eq(e -> e.getTransactionId(), transactionId));
            if (exist != null) {
                log.info("Payment record already exists, reuse it: orderId={}, payChannel={}, transactionId={}",
                        orderId, payChannel, transactionId);
                return exist.getTransactionId();
            }
        }
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(orderId);
        payment.setPayChannel(payChannel);
        payment.setTransactionId(transactionId);
        payment.setAmount(amount);
        payment.setCurrency("USD");
        payment.setStatus(PENDING.name());
        payment.setPaidAt(null);
        paymentMapper.insert(payment);
        return transactionId;
    }
}

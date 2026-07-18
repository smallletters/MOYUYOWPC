package com.moyuyo.service.impl;

import com.moyuyo.common.dto.payment.CreatePaymentRequest;
import com.moyuyo.common.dto.payment.CreatePaymentResponse;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final RestTemplate restTemplate;

    public PaymentServiceImpl(OrderService orderService,
                               PaymentMapper paymentMapper,
                               OrderMapper orderMapper,
                               @Qualifier("restTemplate") RestTemplate restTemplate) {
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.restTemplate = restTemplate;
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

    @Override
    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        OrderEntity order = orderService.getOrderByOrderNo(request.getOrderNo());
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + request.getOrderNo());
        }
        if (!"PENDING_PAY".equals(order.getStatus())) {
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

        String body = "amount=" + amountCents
                + "&currency=" + stripeCurrency
                + "&description=" + "MOYUYO Order " + order.getOrderNo()
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
            throw new RuntimeException("Stripe payment service unavailable: " + e.getMessage());
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
                request.getReturnUrl() != null ? request.getReturnUrl() : "https://moyuyo.com/order/cancel",
                request.getReturnUrl() != null ? request.getReturnUrl().replace("success", "cancel") : "https://moyuyo.com/order/cancel");

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
            throw new RuntimeException("PayPal payment service unavailable: " + e.getMessage());
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

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/v1/oauth2/token", entity, Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new RuntimeException("Failed to get PayPal access token");
        }
        return (String) body.get("access_token");
    }

    @Override
    @Transactional
    public void handleWebhook(String payChannel, String payload, String signature) {
        if ("STRIPE".equalsIgnoreCase(payChannel)) {
            handleStripeWebhook(payload);
        } else if ("PAYPAL".equalsIgnoreCase(payChannel)) {
            handlePayPalWebhook(payload);
        } else {
            log.warn("Unknown webhook channel: {}", payChannel);
        }
    }

    private void handleStripeWebhook(String payload) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> event = mapper.readValue(payload, Map.class);
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
            }
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
        }
    }

    private void handlePayPalWebhook(String payload) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> event = mapper.readValue(payload, Map.class);
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
                            paypalOrderId = href.substring(href.indexOf("/v2/checkout/orders/") + 20);
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
        payment.setStatus("PENDING");
        payment.setPaidAt(null);
        paymentMapper.insert(payment);
    }
}

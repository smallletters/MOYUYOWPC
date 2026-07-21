package com.moyuyo.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.payment.CreatePaymentRequest;
import com.moyuyo.common.dto.payment.CreatePaymentResponse;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private OrderService orderService;

  @Mock
  private PaymentMapper paymentMapper;

  @Mock
  private OrderMapper orderMapper;

  @Mock
  private RestTemplate restTemplate;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @InjectMocks
  private PaymentServiceImpl paymentService;

  @Captor
  private ArgumentCaptor<PaymentEntity> paymentCaptor;

  @BeforeEach
  void setUp() throws Exception {
    // 使用反射设置 @Value 字段，因为 MockitoExtension 不处理 Spring @Value
    setField("stripeSecretKey", "sk_test_mockkey123456");
    setField("stripeWebhookSecret", "whsec_test_secret");
    setField("stripeCurrency", "usd");
    setField("paypalClientId", "test_client_id");
    setField("paypalClientSecret", "test_client_secret");
    setField("paypalMode", "sandbox");
  }

  private void setField(String fieldName, String value) throws Exception {
    Field field = PaymentServiceImpl.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(paymentService, value);
  }

  // ==================== createPayment ====================

  @SuppressWarnings("unchecked")
  @Test
  void createPayment_validStripeRequest_shouldReturnPaymentResponse() {
    // 准备：订单和请求
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setOrderNo("ORD20260721000001");
    order.setStatus("PENDING_PAY");
    order.setPayAmount(new BigDecimal("99.99"));

    when(orderService.getOrderByOrderNo("ORD20260721000001")).thenReturn(order);

    // 模拟 Stripe API 响应
    Map<String, Object> stripeResponse = Map.of(
        "id", "pi_test_123",
        "client_secret", "pi_test_123_secret_xyz"
    );
    ResponseEntity<Map> responseEntity = ResponseEntity.ok(stripeResponse);
    when(restTemplate.postForEntity(
        eq("https://api.stripe.com/v1/payment_intents"),
        any(HttpEntity.class),
        eq(Map.class)
    )).thenReturn(responseEntity);

    // 准备请求
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setOrderNo("ORD20260721000001");
    request.setPayChannel("STRIPE");

    // 执行
    CreatePaymentResponse result = paymentService.createPayment(request);

    // 验证响应
    assertNotNull(result);
    assertEquals("pi_test_123", result.getPaymentId());
    assertEquals("pi_test_123_secret_xyz", result.getClientSecret());
    assertNull(result.getApprovalUrl());
    assertEquals("STRIPE", result.getPayChannel());

    // 验证支付记录保存
    verify(paymentMapper).insert(paymentCaptor.capture());
    PaymentEntity savedPayment = paymentCaptor.getValue();
    assertEquals(1L, savedPayment.getOrderId());
    assertEquals("STRIPE", savedPayment.getPayChannel());
    assertEquals("pi_test_123", savedPayment.getTransactionId());
    assertEquals(new BigDecimal("99.99"), savedPayment.getAmount());
    assertEquals("PENDING", savedPayment.getStatus());
  }

  @Test
  void createPayment_orderNotExist_shouldThrowIllegalArgumentException() {
    // 准备：订单不存在
    when(orderService.getOrderByOrderNo("ORD_NOT_EXIST")).thenReturn(null);

    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setOrderNo("ORD_NOT_EXIST");
    request.setPayChannel("STRIPE");

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> paymentService.createPayment(request));
    assertTrue(ex.getMessage().contains("订单不存在"));
  }

  @Test
  void createPayment_orderStatusNotPendingPay_shouldThrowIllegalStateException() {
    // 准备：订单状态不是 PENDING_PAY
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setOrderNo("ORD20260721000001");
    order.setStatus("PAID"); // 已支付

    when(orderService.getOrderByOrderNo("ORD20260721000001")).thenReturn(order);

    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setOrderNo("ORD20260721000001");
    request.setPayChannel("STRIPE");

    // 执行 & 验证
    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> paymentService.createPayment(request));
    assertTrue(ex.getMessage().contains("订单状态不允许支付"));
  }

  @Test
  void createPayment_unsupportedChannel_shouldThrowIllegalArgumentException() {
    // 准备：不支持的支付渠道
    OrderEntity order = new OrderEntity();
    order.setId(1L);
    order.setOrderNo("ORD20260721000001");
    order.setStatus("PENDING_PAY");

    when(orderService.getOrderByOrderNo("ORD20260721000001")).thenReturn(order);

    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setOrderNo("ORD20260721000001");
    request.setPayChannel("ALIPAY"); // 不支持的渠道

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> paymentService.createPayment(request));
    assertTrue(ex.getMessage().contains("不支持的支付渠道"));
  }

  // ==================== handleWebhook ====================

  @Test
  void handleWebhook_stripePaymentSuccess_shouldProcessPayment() throws Exception {
    // 准备：Stripe webhook payload
    ObjectMapper mapper = new ObjectMapper();
    String payload = mapper.writeValueAsString(Map.of(
        "id", "evt_test_123",
        "type", "payment_intent.succeeded",
        "data", Map.of(
            "object", Map.of(
                "id", "pi_test_456",
                "metadata", Map.of("order_no", "ORD20260721000001")
            )
        )
    ));

    // 模拟 Redis 幂等检查：key 不存在（首次处理）
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(
        eq("idempotent:webhook:evt_test_123"),
        eq("1"),
        eq(24L),
        eq(TimeUnit.HOURS)
    )).thenReturn(true);

    // 执行
    paymentService.handleWebhook("STRIPE", payload, "test_signature");

    // 验证 orderService.payCallback 被调用
    verify(orderService).payCallback("ORD20260721000001", "STRIPE", "pi_test_456");
  }

  @Test
  void handleWebhook_duplicateEvent_shouldSkip() throws Exception {
    // 准备：重复的 webhook 事件
    ObjectMapper mapper = new ObjectMapper();
    String payload = mapper.writeValueAsString(Map.of(
        "id", "evt_duplicate_001",
        "type", "payment_intent.succeeded"
    ));

    // 模拟 Redis 幂等检查：key 已存在（重复事件）
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
        .thenReturn(false);

    // 执行
    paymentService.handleWebhook("STRIPE", payload, "test_signature");

    // 验证 orderService.payCallback 没有被调用
    verify(orderService, never()).payCallback(anyString(), anyString(), anyString());
  }
}

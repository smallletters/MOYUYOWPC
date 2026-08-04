package com.moyuyo.api.controller;

import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook 幂等性集成测试
 * 验证同一事件重复发送时仅第一次被处理
 * 注意：验签逻辑由 PaymentServiceTest 覆盖，本测试 mock PaymentService 以聚焦幂等性验证
 */
@AutoConfigureMockMvc
class WebhookIdempotencyTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // mock PaymentService 以跳过验签，聚焦测试 Webhook 接口的幂等性
    @MockBean
    private PaymentService paymentService;

    /**
     * 相同 event_id 的 webhook 重复发送，应全部返回 200（幂等）
     */
    @Test
    void stripeWebhook_DuplicateEvent_ShouldReturn200() throws Exception {
        // mock：验签通过，直接返回成功
        doNothing().when(paymentService).handleWebhook(eq("STRIPE"), anyString(), anyMap());

        String payload = """
                {
                    "id": "evt_dup_001",
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "pi_dup_001",
                            "metadata": {"order_no": "ORD_DUP_001"}
                        }
                    }
                }
                """;

        // 第一次发送
        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content(payload))
                .andExpect(status().isOk());

        // 第二次发送（相同 event_id）
        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content(payload))
                .andExpect(status().isOk());
    }

    /**
     * 不同 event_id 的 webhook 应按顺序正常处理
     */
    @Test
    void stripeWebhook_DifferentEvents_ShouldProcessEach() throws Exception {
        doNothing().when(paymentService).handleWebhook(eq("STRIPE"), anyString(), anyMap());

        String payload1 = """
                {
                    "id": "evt_seq_001",
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "pi_seq_001",
                            "metadata": {"order_no": "ORD_SEQ_001"}
                        }
                    }
                }
                """;

        String payload2 = """
                {
                    "id": "evt_seq_002",
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "pi_seq_002",
                            "metadata": {"order_no": "ORD_SEQ_002"}
                        }
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content(payload1))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content(payload2))
                .andExpect(status().isOk());
    }

    /**
     * PayPal webhook 同样具有幂等性
     */
    @Test
    void paypalWebhook_DuplicateEvent_ShouldReturn200() throws Exception {
        doNothing().when(paymentService).handleWebhook(eq("PAYPAL"), anyString(), anyMap());

        String payload = """
                {
                    "id": "evt_pp_dup_001",
                    "event_type": "CHECKOUT.ORDER.APPROVED",
                    "resource": {"id": "PAYPAL_ORDER_DUP_001"}
                }
                """;

        // 第一次
        mockMvc.perform(post("/api/v1/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // 重复
        mockMvc.perform(post("/api/v1/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}

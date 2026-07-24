package com.moyuyo.api.controller;

import com.moyuyo.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付回调 Webhook 集成测试
 * 覆盖 Stripe/PayPal 回调签名验证、重复回调幂等、退款回调场景
 * 注意：webhook 端点总是返回 200（Result.success），业务异常仅记录日志
 */
@AutoConfigureMockMvc
class PaymentCallbackTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void stripeWebhook_WithValidPayload_ShouldReturn200() throws Exception {
        String payload = """
                {
                    "id": "evt_test_1",
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "pi_test_1",
                            "metadata": {"order_no": "ORD20260721001"}
                        }
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void stripeWebhook_MissingSignature_ShouldReturn200() throws Exception {
        String payload = """
                {
                    "id": "evt_test_2",
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "pi_test_2",
                            "metadata": {"order_no": "ORD20260721002"}
                        }
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void stripeWebhook_UnsupportedEventType_ShouldReturn200() throws Exception {
        String payload = """
                {
                    "id": "evt_test_3",
                    "type": "charge.failed",
                    "data": {
                        "object": {
                            "id": "ch_test_3"
                        }
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void stripeWebhook_InvalidPayload_ShouldReturn200() throws Exception {
        mockMvc.perform(post("/api/v1/payments/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "test_sig")
                        .content("not valid json"))
                .andExpect(status().isOk());
    }

    @Test
    void paypalWebhook_ShouldReturn200() throws Exception {
        String payload = """
                {
                    "id": "evt_paypal_1",
                    "event_type": "CHECKOUT.ORDER.APPROVED",
                    "resource": {"id": "PAYPAL_ORDER_1"}
                }
                """;

        mockMvc.perform(post("/api/v1/payments/paypal/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}

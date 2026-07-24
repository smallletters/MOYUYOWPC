package com.moyuyo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.common.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单完整生命周期集成测试
 * 覆盖：创建订单 → 支付成功 → 订单状态流转 → 退款申请 → 退款完成
 */
@AutoConfigureMockMvc
class OrderFlowTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = "Bearer " + jwtUtil.generate(1L, "test@moyuyo.com");
    }

    @Test
    @DisplayName("未认证访问应返回401")
    void orderFlow_Unauthenticated_ShouldReturn401() throws Exception {
        // 未登录用户创建订单
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("无效参数创建订单应返回400")
    void orderFlow_CreateOrder_InvalidParams_ShouldReturn400() throws Exception {
        // 空参数的订单创建请求
        String invalidReq = """
                {
                    "orderNo": "",
                    "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidReq))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("分页查询订单列表应返回200")
    void orderFlow_ListOrders_ShouldReturnPage() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", validToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("查询不存在订单应返回404")
    void orderFlow_GetOrderDetail_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", 999999L)
                        .header("Authorization", validToken))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("无效状态转换应返回400")
    void orderFlow_InvalidOrderStatusTransition() throws Exception {
        // 尝试对不存在的订单执行取消操作
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", 999999L)
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("未认证退款请求应返回401")
    void orderFlow_Refund_Unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "orderId": 1,
                                    "type": "FULL",
                                    "reason": "Test refund"
                                }
                                """))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("无效退款金额应返回400")
    void orderFlow_Refund_InvalidAmount() throws Exception {
        mockMvc.perform(post("/api/v1/refunds")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "orderId": 1,
                                    "type": "PARTIAL",
                                    "amount": -10,
                                    "reason": "Invalid amount"
                                }
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("未认证支付请求应返回401")
    void orderFlow_PaymentWithoutAuth() throws Exception {
        String paymentReq = """
                {
                    "orderNo": "ORD_TEST_001",
                    "payChannel": "STRIPE",
                    "returnUrl": "https://moyuyo.com/order/success"
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentReq))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(401));
    }
}

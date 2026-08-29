package com.moyuyo.api.controller;

import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.common.dto.payment.CreatePaymentRequest;
import com.moyuyo.common.dto.payment.CreatePaymentResponse;
import com.moyuyo.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PaymentServiceTest extends BaseIntegrationTest {

    @MockBean
    private PaymentService paymentService;

    @Test
    void createPayment_ShouldReturnResponse() {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setOrderNo("ORD-TEST-001");
        req.setPayChannel("STRIPE");

        CreatePaymentResponse mockRes = new CreatePaymentResponse(
                "pi_test_123", "secret_test", null, null, null, "STRIPE");
        when(paymentService.createPayment(anyLong(), any())).thenReturn(mockRes);

        CreatePaymentResponse result = paymentService.createPayment(1L, req);
        assertNotNull(result);
        assertEquals("pi_test_123", result.getPaymentId());
        assertEquals("STRIPE", result.getPayChannel());
    }
}

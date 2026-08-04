package com.moyuyo.service;

import com.moyuyo.common.dto.payment.CreatePaymentRequest;
import com.moyuyo.common.dto.payment.CreatePaymentResponse;

import java.util.Map;

public interface PaymentService {

    // 发起支付需传入当前登录用户 ID，用于校验订单归属，防止越权支付他人订单
    CreatePaymentResponse createPayment(Long userId, CreatePaymentRequest request);

    // 处理支付回调需传入请求头 Map，用于校验网关签名
    void handleWebhook(String payChannel, String payload, Map<String, String> headers);
}

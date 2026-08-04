package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.payment.CreatePaymentRequest;
import com.moyuyo.common.dto.payment.CreatePaymentResponse;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.PaymentService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "支付管理")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PayController {

    private final PaymentService paymentService;

    @Operation(summary = "发起支付")
    @PostMapping("/create")
    @RateLimiter(name = "paymentApi", fallbackMethod = "createPaymentRateLimitFallback")
    public Result<CreatePaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        // 传入当前登录用户 ID，用于校验订单归属
        return Result.success(paymentService.createPayment(UserContextHolder.getUserId(), request));
    }

    /** 支付创建限流降级方法 */
    @SuppressWarnings("unused")
    private Result<CreatePaymentResponse> createPaymentRateLimitFallback(CreatePaymentRequest request, RequestNotPermitted e) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    @Operation(summary = "Stripe Webhook 回调")
    @PostMapping("/stripe/webhook")
    public Result<Void> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        // 收集验签所需请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("stripe-signature", signature);
        paymentService.handleWebhook("STRIPE", payload, headers);
        return Result.success();
    }

    @Operation(summary = "PayPal Webhook 回调")
    @PostMapping("/paypal/webhook")
    public Result<Void> paypalWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig) {
        // 收集 PayPal 验签所需的全部请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("paypal-transmission-id", transmissionId);
        headers.put("paypal-transmission-time", transmissionTime);
        headers.put("paypal-cert-url", certUrl);
        headers.put("paypal-auth-algo", authAlgo);
        headers.put("paypal-transmission-sig", transmissionSig);
        paymentService.handleWebhook("PAYPAL", payload, headers);
        return Result.success();
    }
}

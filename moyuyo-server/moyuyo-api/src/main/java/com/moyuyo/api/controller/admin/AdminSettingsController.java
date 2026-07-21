package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 — 设置相关接口
 * 原 AdminController 拆分而来，负责系统设置项
 */
@Tag(name = "管理后台 - 设置")
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    @Operation(summary = "获取支付方式")
    @GetMapping("/payment-methods")
    public Result<List<Map<String, String>>> paymentMethods() {
        List<Map<String, String>> methods = new ArrayList<>();
        Map<String, String> stripe = new LinkedHashMap<>();
        stripe.put("name", "Stripe");
        stripe.put("code", "stripe");
        stripe.put("status", "active");
        methods.add(stripe);
        Map<String, String> paypal = new LinkedHashMap<>();
        paypal.put("name", "PayPal");
        paypal.put("code", "paypal");
        paypal.put("status", "active");
        methods.add(paypal);
        return Result.success(methods);
    }
}

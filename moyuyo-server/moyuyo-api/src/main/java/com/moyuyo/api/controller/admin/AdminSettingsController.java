package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.config.PaymentMethodConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 — 设置相关接口
 * 原 AdminController 拆分而来，负责系统设置项
 */
@Tag(name = "管理后台 - 设置")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PaymentMethodConfigService paymentMethodConfigService;

    @Operation(summary = "获取支付方式开关（与 C 端收银台同源）")
    @GetMapping("/payment-methods")
    public Result<List<Map<String, Object>>> paymentMethods() {
        // 与管理端「支付设置」保存的 payment_{code} 开关同源，
        // 返回 enabled 让后台直接渲染真实状态，不再按历史支付流水反推。
        return Result.success(paymentMethodConfigService.listSwitches());
    }
}

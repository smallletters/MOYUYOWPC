package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.mapper.PaymentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PaymentMapper paymentMapper;

    @Operation(summary = "获取支付方式")
    @GetMapping("/payment-methods")
    public Result<List<Map<String, String>>> paymentMethods() {
        // 从数据库查询已有的支付渠道
        List<PaymentEntity> channels = paymentMapper.selectList(
            Wrappers.<PaymentEntity>lambdaQuery()
                .select(PaymentEntity::getPayChannel)
                .groupBy(PaymentEntity::getPayChannel)
        );

        List<Map<String, String>> methods = new ArrayList<>();
        if (!channels.isEmpty()) {
            // 数据库中有记录，使用数据库数据
            for (PaymentEntity channel : channels) {
                Map<String, String> item = new LinkedHashMap<>();
                String code = channel.getPayChannel();
                item.put("name", formatChannelName(code));
                item.put("code", code);
                item.put("status", "active");
                methods.add(item);
            }
        } else {
            // 数据库为空时，使用硬编码默认数据
            methods.add(buildPaymentMethod("Stripe", "stripe"));
            methods.add(buildPaymentMethod("PayPal", "paypal"));
        }
        return Result.success(methods);
    }

    /**
     * 构建支付方式映射
     */
    private Map<String, String> buildPaymentMethod(String name, String code) {
        Map<String, String> method = new LinkedHashMap<>();
        method.put("name", name);
        method.put("code", code);
        method.put("status", "active");
        return method;
    }

    /**
     * 格式化支付渠道代码为显示名称
     */
    private String formatChannelName(String code) {
        if (code == null) return "";
        switch (code.toLowerCase()) {
            case "stripe": return "Stripe";
            case "paypal": return "PayPal";
            case "alipay": return "支付宝";
            case "wechat": return "微信支付";
            default: return code.substring(0, 1).toUpperCase() + code.substring(1);
        }
    }
}

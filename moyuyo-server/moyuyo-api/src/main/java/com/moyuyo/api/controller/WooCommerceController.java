package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.service.WooCommerceSyncService;
import com.moyuyo.service.impl.WooCommerceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "WooCommerce 同步")
@RestController
@RequestMapping("/api/v1/woocommerce")
@RequiredArgsConstructor
public class WooCommerceController {

    private final WooCommerceSyncService syncService;
    private final WooCommerceClient client;

    @Operation(summary = "测试连接")
    @GetMapping("/test")
    public Result<Map<String, Object>> testConnection() {
        boolean connected = client.verifyConnection();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("connected", connected);
        data.put("url", client.getBaseUrl());
        return connected ? Result.success(data) : Result.error(503, "WooCommerce connection failed");
    }

    @Operation(summary = "同步商品和分类")
    @PostMapping("/sync/products")
    public Result<Map<String, Object>> syncProducts() {
        syncService.syncCategoriesFromWooCommerce();
        syncService.syncProductsFromWooCommerce();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", true);
        data.put("message", "Products and categories sync initiated");
        return Result.success(data);
    }

    @Operation(summary = "同步待处理订单")
    @PostMapping("/sync/orders")
    public Result<Map<String, Object>> syncOrders() {
        syncService.syncAllPendingOrders();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", true);
        data.put("message", "Pending orders sync initiated");
        return Result.success(data);
    }

    @Operation(summary = "获取商品列表（来自 WooCommerce）")
    @GetMapping("/products")
    public Result<List<Map<String, Object>>> getProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        if (!client.isConfigured()) {
            return Result.error(503, "WooCommerce 未配置，请先设置 URL 与认证信息");
        }
        List<Map<String, Object>> products = client.getProducts(page, perPage);
        return Result.success(products);
    }

    @Operation(summary = "获取分类列表（来自 WooCommerce）")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> getCategories() {
        if (!client.isConfigured()) {
            return Result.error(503, "WooCommerce 未配置，请先设置 URL 与认证信息");
        }
        List<Map<String, Object>> categories = client.getCategories();
        return Result.success(categories);
    }

    @Operation(summary = "获取订单列表（来自 WooCommerce）")
    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> getOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        if (!client.isConfigured()) {
            return Result.error(503, "WooCommerce 未配置，请先设置 URL 与认证信息");
        }
        List<Map<String, Object>> orders = client.getOrders(page, perPage);
        return Result.success(orders);
    }
}

package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.service.WooCommerceSyncService;
import com.moyuyo.service.impl.WooCommerceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@Tag(name = "管理后台 - WooCommerce 同步")
@RestController
// 安全修复：该接口会读取全站 WooCommerce 订单（含买家 PII）并触发商品/订单同步，
// 属于管理端能力。迁移到 /api/admin/** 后由 AdminPermissionFilter 做 RBAC 校验，
// 避免任意已登录 C 端用户读取订单数据或触发同步写操作。
@RequestMapping("/api/admin/woocommerce")
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
        try {
            syncService.syncCategoriesFromWooCommerce();
            syncService.syncProductsFromWooCommerce();
        } catch (Exception e) {
            // 安全修复：上游失败不再返回“同步成功”，避免把 0 条当作成功
            log.error("WooCommerce 商品/分类同步失败", e);
            return Result.error(502, "WooCommerce 同步失败，请检查配置或稍后重试");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", true);
        data.put("message", "Products and categories sync completed");
        return Result.success(data);
    }

    @Operation(summary = "同步待处理订单")
    @PostMapping("/sync/orders")
    public Result<Map<String, Object>> syncOrders() {
        try {
            syncService.syncAllPendingOrders();
        } catch (Exception e) {
            log.error("WooCommerce 待处理订单同步失败", e);
            return Result.error(502, "WooCommerce 订单同步失败，请检查配置或稍后重试");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("synced", true);
        data.put("message", "Pending orders sync completed");
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
        try {
            List<Map<String, Object>> products = client.getProducts(page, perPage);
            return Result.success(products);
        } catch (Exception e) {
            log.error("查询 WooCommerce 商品失败", e);
            return Result.error(502, "WooCommerce 商品查询失败，请稍后重试");
        }
    }

    @Operation(summary = "获取分类列表（来自 WooCommerce）")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> getCategories() {
        if (!client.isConfigured()) {
            return Result.error(503, "WooCommerce 未配置，请先设置 URL 与认证信息");
        }
        try {
            List<Map<String, Object>> categories = client.getCategories();
            return Result.success(categories);
        } catch (Exception e) {
            log.error("查询 WooCommerce 分类失败", e);
            return Result.error(502, "WooCommerce 分类查询失败，请稍后重试");
        }
    }

    @Operation(summary = "获取订单列表（来自 WooCommerce）")
    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> getOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        if (!client.isConfigured()) {
            return Result.error(503, "WooCommerce 未配置，请先设置 URL 与认证信息");
        }
        try {
            List<Map<String, Object>> orders = client.getOrders(page, perPage);
            return Result.success(orders);
        } catch (Exception e) {
            log.error("查询 WooCommerce 订单失败", e);
            return Result.error(502, "WooCommerce 订单查询失败，请稍后重试");
        }
    }
}

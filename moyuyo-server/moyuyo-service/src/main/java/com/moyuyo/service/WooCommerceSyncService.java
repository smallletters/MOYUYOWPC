package com.moyuyo.service;

import com.moyuyo.dao.entity.ProductEntity;

import java.util.Map;

public interface WooCommerceSyncService {

    // ===== 订单同步 =====

    /**
     * 将本地订单推送到 WooCommerce（已同步则跳过）。
     * 失败时在订单上记录 syncStatus=-1，不抛异常（不影响主流程）。
     */
    void syncOrderToWooCommerce(com.moyuyo.dao.entity.OrderEntity order);

    /**
     * 把状态变更同步到 WooCommerce 侧订单。
     */
    void syncOrderStatus(Long wooOrderId, String status);

    /**
     * 批量重试 syncStatus=-1 或 wooOrderId 为空的订单。
     */
    void syncAllPendingOrders();

    // ===== 商品拉取 =====

    /**
     * 分页拉取 WooCommerce 全量商品到本地。
     */
    void syncProductsFromWooCommerce();

    /**
     * 拉取 WooCommerce 全量分类。
     */
    void syncCategoriesFromWooCommerce();

    // ===== 商品推送 =====

    /**
     * 将单个商品推送到 WooCommerce。
     * @return WooCommerce 上的商品 id；推送失败返回 null
     */
    Long pushProductToWooCommerce(ProductEntity product);

    /**
     * 将本地商品更新到 WooCommerce（按 wooProductId）。
     * @return 是否成功
     */
    boolean updateProductOnWooCommerce(ProductEntity product);

    /**
     * 批量推送本地所有未同步到 WooCommerce 的商品。
     * @return 统计 { total, success, failed, skipped }
     */
    Map<String, Integer> pushAllProductsToWooCommerce();

    /**
     * 删除 WooCommerce 上对应商品（按 wooProductId）。
     */
    boolean deleteProductFromWooCommerce(Long wooProductId);

    // ===== 单商品拉取 =====

    /**
     * 从 WooCommerce 拉取单个商品到本地（按本地商品 ID，需已录入 wooProductId）。
     * @param productId 本地商品 ID
     * @return 更新后的商品实体，失败返回 null
     */
    ProductEntity pullProductFromWooCommerce(Long productId);

    // ===== 库存同步 =====

    /**
     * 从 WooCommerce 拉取单个商品的库存（仅更新 stock/stockStatus/manageStock，不覆盖其他字段）。
     * @param productId 本地商品 ID（需已关联 wooProductId）
     * @return 更新后的商品实体，失败返回 null
     */
    ProductEntity syncStockFromWooCommerce(Long productId);

    /**
     * 从 WooCommerce 批量拉取所有关联商品的库存。
     * @return Map: { total, updated, skipped, failed }
     */
    Map<String, Integer> syncAllStocksFromWooCommerce();
}

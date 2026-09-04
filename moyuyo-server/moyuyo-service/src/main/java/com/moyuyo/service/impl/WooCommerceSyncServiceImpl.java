package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductImageEntity;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductImageMapper;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.service.WooCommerceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class WooCommerceSyncServiceImpl implements WooCommerceSyncService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final WooCommerceClient client;
    private final ObjectMapper objectMapper;

    // ============ 订单运单同步（WC → 本地） ============

    /**
     * 从 WooCommerce 拉取订单最新承运商/运单号，并回写到本地订单。
     * <p>
     * WC REST API 中运单号存储位置（按优先级解析）：
     *   1) meta_data 中 _tracking_number + _tracking_provider（woocommerce-shipment-tracking 插件）
     *   2) meta_data 中 shipment_tracking_*（Advanced Shipment Tracking 等插件）
     *   3) meta_data 中 ast_tracking_number / ast_carrier
     * <p>
     * 仅当 WC 返回了运单号且与本地不一致时才更新，避免无效写库。
     * 同步失败抛 WooCommerceSyncException，由 controller 决定是否降级（返回本地缓存值）。
     */
    public OrderEntity syncOrderTrackingFromWooCommerce(Long orderId) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) return null;
        Long wooOrderId = order.getWooOrderId();
        if (wooOrderId == null) {
            // 本地订单未推送到 WC，无运单可言
            return order;
        }
        Map<String, Object> wcOrder;
        try {
            wcOrder = client.getOrder(wooOrderId.intValue());
        } catch (Exception e) {
            log.warn("[woo-tracking] 拉取 WC 订单失败：orderId={}, wooOrderId={}, reason={}",
                    orderId, wooOrderId, e.getMessage());
            return order;
        }
        if (wcOrder == null || wcOrder.isEmpty()) return order;

        String wcTracking = extractMetaString(wcOrder, "_tracking_number", "shipment_tracking_number", "ast_tracking_number");
        String wcCarrier = extractMetaString(wcOrder, "_tracking_provider", "shipment_tracking_provider", "ast_carrier", "_tracking_company");

        boolean changed = false;
        if (wcTracking != null && !wcTracking.isBlank() && !wcTracking.equals(order.getTrackingNumber())) {
            order.setTrackingNumber(wcTracking);
            changed = true;
        }
        if (wcCarrier != null && !wcCarrier.isBlank() && !wcCarrier.equals(order.getShippingCarrier())) {
            order.setShippingCarrier(wcCarrier);
            changed = true;
        }
        if (changed) {
            orderMapper.updateById(order);
            log.info("[woo-tracking] 同步运单成功：orderId={}, wooOrderId={}, carrier={}, tracking={}",
                    orderId, wooOrderId, order.getShippingCarrier(), order.getTrackingNumber());
        }
        return order;
    }

    /** 从 WC 订单 meta_data 数组中按多个候选 key 提取首个非空字符串值 */
    @SuppressWarnings("unchecked")
    private String extractMetaString(Map<String, Object> wcOrder, String... keys) {
        Object metaObj = wcOrder.get("meta_data");
        if (!(metaObj instanceof List) || keys == null) return null;
        for (Object entry : (List<Object>) metaObj) {
            if (!(entry instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) entry;
            Object key = m.get("key");
            if (key == null) continue;
            String keyStr = key.toString();
            for (String k : keys) {
                if (k.equals(keyStr)) {
                    Object v = m.get("value");
                    if (v != null && !v.toString().isBlank()) return v.toString();
                }
            }
        }
        return null;
    }

    // ============ 订单同步 ============

    @Override
    @Transactional
    public void syncOrderToWooCommerce(OrderEntity order) {
        if (order == null) return;
        if (order.getWooOrderId() != null) {
            log.info("Order already synced: orderNo={}, wooOrderId={}",
                    order.getOrderNo(), order.getWooOrderId());
            return;
        }

        try {
            Map<String, Object> orderData = buildOrderData(order);
            Map<String, Object> created = client.createOrder(orderData);
            Number wooId = (Number) created.get("id");

            order.setWooOrderId(wooId.longValue());
            order.setSyncStatus(1);
            order.setSyncRetryCount(0);
            order.setSyncLastTime(LocalDateTime.now());
            orderMapper.updateById(order);

            log.info("WooCommerce sync success: orderNo={}, wooOrderId={}",
                    order.getOrderNo(), wooId);
        } catch (Exception e) {
            // 失败记录状态，不抛异常（避免影响支付回调主流程）
            order.setSyncStatus(-1);
            order.setSyncRetryCount(order.getSyncRetryCount() == null ? 1 : order.getSyncRetryCount() + 1);
            order.setSyncLastTime(LocalDateTime.now());
            orderMapper.updateById(order);
            log.error("WooCommerce sync failed: orderNo={}, reason={}",
                    order.getOrderNo(), e.getMessage());
        }
    }

    @Override
    public void syncOrderStatus(Long wooOrderId, String status) {
        if (wooOrderId == null) return;
        try {
            client.updateOrderStatus(wooOrderId.intValue(), status);
            log.info("WooCommerce status sync: wooOrderId={}, status={}", wooOrderId, status);
        } catch (Exception e) {
            log.error("WooCommerce status sync failed: wooOrderId={}, reason={}",
                    wooOrderId, e.getMessage());
        }
    }

    @Override
    public void syncAllPendingOrders() {
        List<OrderEntity> pending = orderMapper.selectList(
                new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getSyncStatus, -1)
                        .or()
                        .isNull(OrderEntity::getWooOrderId));
        for (OrderEntity order : pending) {
            syncOrderToWooCommerce(order);
        }
        log.info("WooCommerce sync all pending: count={}", pending.size());
    }

    // ============ 商品拉取（已有）============

    @Override
    public void syncProductsFromWooCommerce() {
        try {
            int page = 1;
            int perPage = 100;
            int totalSynced = 0;

            while (true) {
                List<Map<String, Object>> products = client.getProducts(page, perPage);
                if (products == null || products.isEmpty()) break;

                for (Map<String, Object> wp : products) {
                    try {
                        syncSingleProduct(wp);
                        totalSynced++;
                    } catch (Exception e) {
                        log.error("Failed to sync product: id={}, reason={}", wp.get("id"), e.getMessage());
                    }
                }
                page++;
            }
            log.info("WooCommerce product sync completed: total={}", totalSynced);
        } catch (Exception e) {
            log.error("WooCommerce product sync failed", e);
        }
    }

    private void syncSingleProduct(Map<String, Object> wp) {
        Number wooId = (Number) wp.get("id");
        String name = (String) wp.get("name");
        String description = (String) wp.get("description");
        String shortDescription = (String) wp.get("short_description");
        String status = (String) wp.get("status");
        String sku = (String) wp.get("sku");
        String type = (String) wp.get("type");
        String weightStr = (String) wp.get("weight");
        Boolean manageStock = (Boolean) wp.get("manage_stock");

        ProductEntity existing = productMapper.selectOne(
                new LambdaQueryWrapper<ProductEntity>()
                        .eq(ProductEntity::getWooProductId, wooId.longValue()));

        ProductEntity product = existing != null ? existing : new ProductEntity();
        if (existing == null) {
            product.setWooProductId(wooId.longValue());
        }
        product.setName(name);
        product.setDetail(description);
        product.setShortDetail(shortDescription);

        // 同步 SKU（仅当本地未设置或为空时覆盖，避免覆盖手动录入值）
        String existingSpu = product.getSpuCode();
        if ((existingSpu == null || existingSpu.isBlank()) && sku != null && !sku.isBlank()) {
            product.setSpuCode(sku);
        }

        // 同步产品类型
        product.setProductType(type != null ? type : "simple");

        // 同步重量
        if (weightStr != null && !weightStr.isBlank()) {
            try {
                product.setWeight(new BigDecimal(weightStr));
            } catch (NumberFormatException e) {
                log.warn("Invalid weight value from WC: productId={}, weight={}", wooId, weightStr);
            }
        }

        // 同步库存管理
        product.setManageStock(manageStock != null && manageStock);

        // 同步标签
        List<Map<String, Object>> wcTags = (List<Map<String, Object>>) wp.get("tags");
        if (wcTags != null && !wcTags.isEmpty()) {
            String tagNames = wcTags.stream()
                    .map(t -> (String) t.get("name"))
                    .filter(n -> n != null && !n.isBlank())
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
            product.setTags(tagNames);
        }

        // 同步尺寸 (dimensions: {length, width, height})，存储到 attributes JSON 字段
        Map<String, Object> dimensions = (Map<String, Object>) wp.get("dimensions");
        if (dimensions != null) {
            Map<String, Object> attrs = parseAttributes(product.getAttributes());
            if (!dimensions.isEmpty()) {
                attrs.put("dimensions", dimensions);
            }
            try {
                product.setAttributes(objectMapper.writeValueAsString(attrs));
            } catch (Exception e) {
                log.warn("Failed to serialize dimensions to attributes JSON: {}", e.getMessage());
            }
        }

        // 同步分类
        List<Map<String, Object>> categories = (List<Map<String, Object>>) wp.get("categories");
        if (categories != null && !categories.isEmpty()) {
            Number catId = (Number) categories.get(0).get("id");
            product.setCategoryId(catId.longValue());
            syncCategory(categories.get(0));
        }

        // 同步价格
        String regularPrice = (String) wp.get("regular_price");
        String salePrice = (String) wp.get("sale_price");
        if (regularPrice != null) {
            product.setOriginalPrice(new BigDecimal(regularPrice));
        }
        if (salePrice != null && !salePrice.isBlank()) {
            product.setPrice(new BigDecimal(salePrice));
        } else if (regularPrice != null) {
            product.setPrice(new BigDecimal(regularPrice));
        }

        // 同步库存
        Number stockQuantity = (Number) wp.get("stock_quantity");
        product.setStock(stockQuantity != null ? stockQuantity.intValue() : 0);

        // 同步图片
        List<Map<String, Object>> images = (List<Map<String, Object>>) wp.get("images");
        if (images != null && !images.isEmpty()) {
            product.setMainImage((String) images.get(0).get("src"));
        }

        product.setOnSale("publish".equals(status));
        if (existing == null) {
            product.setCreateTime(LocalDateTime.now());
        }
        product.setUpdateTime(LocalDateTime.now());
        product.setWooModified(LocalDateTime.now());

        if (existing != null) {
            productMapper.updateById(product);
        } else {
            productMapper.insert(product);
        }

        // 同步商品图库到 mo_product_image 子表（仅新建商品时全量覆盖，避免覆盖人工维护的图库）
        if (existing == null && images != null && !images.isEmpty()) {
            syncProductImages(product.getId(), images);
        }
    }

    /**
     * 将 WooCommerce images[] 写入 mo_product_image 子表
     * @param productId 新建后的本地商品 id
     * @param images    WC 返回的图片数组（每项含 src/name/alt）
     */
    private void syncProductImages(Long productId, List<Map<String, Object>> images) {
        try {
            int sort = 0;
            for (Map<String, Object> img : images) {
                String src = (String) img.get("src");
                if (src == null || src.isBlank()) continue;
                ProductImageEntity entity = new ProductImageEntity();
                entity.setProductId(productId);
                entity.setUrl(src);
                entity.setSort(sort++);
                productImageMapper.insert(entity);
            }
            log.info("Synced product images: productId={}, total={}", productId, sort);
        } catch (Exception e) {
            // 图库同步失败不影响主商品，只记录日志
            log.warn("Failed to sync product images: productId={}, reason={}", productId, e.getMessage());
        }
    }

    private void syncCategory(Map<String, Object> wcCat) {
        try {
            Number catId = (Number) wcCat.get("id");
            String catName = (String) wcCat.get("name");
            CategoryEntity existing = categoryMapper.selectById(catId.longValue());
            if (existing == null) {
                CategoryEntity category = new CategoryEntity();
                category.setId(catId.longValue());
                category.setName(catName);
                category.setCreateTime(LocalDateTime.now());
                categoryMapper.insert(category);
                log.info("Synced category: id={}, name={}", catId, catName);
            }
        } catch (Exception e) {
            log.warn("Failed to sync category: {}", e.getMessage());
        }
    }

    @Override
    public void syncCategoriesFromWooCommerce() {
        try {
            List<Map<String, Object>> categories = client.getCategories();
            int synced = 0;
            for (Map<String, Object> wcCat : categories) {
                syncCategory(wcCat);
                synced++;
            }
            log.info("WooCommerce category sync completed: total={}", synced);
        } catch (Exception e) {
            log.error("WooCommerce category sync failed", e);
        }
    }

    // ============ 商品推送（新增）============

    @Override
    @Transactional
    public Long pushProductToWooCommerce(ProductEntity product) {
        if (product == null) return null;
        // dev默认未配置 WooCommerce（url为占位符/consumer-key为空），跳过推送避免刷错误日志
        if (client == null || !client.isConfigured()) {
            log.debug("WooCommerce 未配置或 client 未初始化，跳过推送 productId={}", product.getId());
            return null;
        }
        if (product.getWooProductId() != null) {
            // 已同步过，转用更新
            boolean ok = updateProductOnWooCommerce(product);
            return ok ? product.getWooProductId() : null;
        }

        try {
            Map<String, Object> data = buildProductData(product);
            Map<String, Object> created = client.createProduct(data);
            Number wooId = (Number) created.get("id");
            if (wooId == null) {
                log.error("WooCommerce create product returned no id: productId={}", product.getId());
                return null;
            }
            // 回写本地 wooProductId + 标记
            product.setWooProductId(wooId.longValue());
            product.setWooModified(LocalDateTime.now());
            productMapper.updateById(product);
            log.info("WooCommerce push product success: productId={}, wooProductId={}",
                    product.getId(), wooId);
            return wooId.longValue();
        } catch (Exception e) {
            log.error("WooCommerce push product failed: productId={}, reason={}",
                    product.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public boolean updateProductOnWooCommerce(ProductEntity product) {
        if (product == null || product.getWooProductId() == null) {
            log.warn("updateProductOnWooCommerce: product or wooProductId is null");
            return false;
        }
        // dev默认未配置 WooCommerce 时直接跳过
        if (client == null || !client.isConfigured()) {
            log.debug("WooCommerce 未配置，跳过更新 productId={}", product.getId());
            return false;
        }
        try {
            Map<String, Object> data = buildProductData(product);
            client.updateProduct(product.getWooProductId().intValue(), data);
            product.setWooModified(LocalDateTime.now());
            productMapper.updateById(product);
            log.info("WooCommerce update product success: productId={}, wooProductId={}",
                    product.getId(), product.getWooProductId());
            return true;
        } catch (Exception e) {
            log.error("WooCommerce update product failed: productId={}, reason={}",
                    product.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Integer> pushAllProductsToWooCommerce() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("total", 0);
        stats.put("success", 0);
        stats.put("failed", 0);
        stats.put("skipped", 0);

        // 查询所有未同步到 WooCommerce 的商品（按 wooProductId 为空判断）
        List<ProductEntity> all = productMapper.selectList(
                new LambdaQueryWrapper<ProductEntity>()
                        .isNull(ProductEntity::getWooProductId));
        stats.put("total", all.size());

        for (ProductEntity p : all) {
            try {
                Long wooId = pushProductToWooCommerce(p);
                if (wooId != null) {
                    stats.put("success", stats.get("success") + 1);
                } else {
                    stats.put("failed", stats.get("failed") + 1);
                }
            } catch (Exception e) {
                // 单个失败不影响整体
                stats.put("failed", stats.get("failed") + 1);
                log.error("Batch push product failed: productId={}, reason={}",
                        p.getId(), e.getMessage());
            }
        }
        log.info("WooCommerce push all products done: {}", stats);
        return stats;
    }

    @Override
    public boolean deleteProductFromWooCommerce(Long wooProductId) {
        if (wooProductId == null) return false;
        try {
            return client.deleteProduct(wooProductId.intValue());
        } catch (Exception e) {
            log.error("WooCommerce delete product failed: wooProductId={}, reason={}",
                    wooProductId, e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional
    public ProductEntity pullProductFromWooCommerce(Long productId) {
        if (productId == null) {
            log.warn("pullProductFromWooCommerce: productId is null");
            return null;
        }
        // 查询本地商品
        ProductEntity localProduct = productMapper.selectById(productId);
        if (localProduct == null) {
            log.warn("pullProductFromWooCommerce: local product not found, id={}", productId);
            return null;
        }
        // 必须已关联 WooCommerce 商品才能拉取
        if (localProduct.getWooProductId() == null) {
            log.warn("pullProductFromWooCommerce: local product has no wooProductId, id={}", productId);
            return null;
        }
        try {
            // 从 WooCommerce 获取最新数据
            Map<String, Object> wcProduct = client.getProduct(localProduct.getWooProductId().intValue());
            if (wcProduct == null) {
                log.warn("pullProductFromWooCommerce: WC product not found, wooId={}", localProduct.getWooProductId());
                return null;
            }
            // 复用单商品同步逻辑
            syncSingleProduct(wcProduct);
            // 返回已更新的本地商品
            return productMapper.selectById(productId);
        } catch (Exception e) {
            log.error("pullProductFromWooCommerce failed: productId={}, reason={}", productId, e.getMessage());
            return null;
        }
    }

    // ============ 库存同步 ============

    @Override
    @Transactional
    public ProductEntity syncStockFromWooCommerce(Long productId) {
        if (productId == null) return null;
        ProductEntity localProduct = productMapper.selectById(productId);
        if (localProduct == null || localProduct.getWooProductId() == null) {
            log.warn("syncStockFromWooCommerce: product not found or no wooProductId, id={}", productId);
            return null;
        }
        try {
            Map<String, Object> wcProduct = client.getProduct(localProduct.getWooProductId().intValue());
            if (wcProduct == null) return null;

            // 仅同步库存相关字段，不动其他字段
            Number stockQuantity = (Number) wcProduct.get("stock_quantity");
            String stockStatus = (String) wcProduct.get("stock_status");
            Boolean manageStock = (Boolean) wcProduct.get("manage_stock");

            boolean changed = false;
            if (stockQuantity != null) {
                int newStock = stockQuantity.intValue();
                if (localProduct.getStock() == null || localProduct.getStock() != newStock) {
                    localProduct.setStock(newStock);
                    changed = true;
                }
            }
            if (stockStatus != null) {
                String newStatus = convertWooStockStatus(stockStatus);
                if (!newStatus.equals(localProduct.getStockStatus())) {
                    localProduct.setStockStatus(newStatus);
                    changed = true;
                }
            }
            if (manageStock != null) {
                if (localProduct.getManageStock() == null || !localProduct.getManageStock().equals(manageStock)) {
                    localProduct.setManageStock(manageStock);
                    changed = true;
                }
            }

            if (changed) {
                localProduct.setWooModified(LocalDateTime.now());
                productMapper.updateById(localProduct);
                log.info("Stock synced from WC: productId={}, stock={}, status={}", productId, stockQuantity, stockStatus);
            }
            return localProduct;
        } catch (Exception e) {
            log.error("syncStockFromWooCommerce failed: productId={}, reason={}", productId, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Integer> syncAllStocksFromWooCommerce() {
        Map<String, Integer> stat = new LinkedHashMap<>();
        stat.put("total", 0);
        stat.put("updated", 0);
        stat.put("skipped", 0);
        stat.put("failed", 0);

        // 查询所有已关联 WC 的商品
        List<ProductEntity> associated = productMapper.selectList(
                new LambdaQueryWrapper<ProductEntity>()
                        .isNotNull(ProductEntity::getWooProductId));
        stat.put("total", associated.size());

        for (ProductEntity p : associated) {
            try {
                ProductEntity result = syncStockFromWooCommerce(p.getId());
                if (result != null) {
                    stat.put("updated", stat.get("updated") + 1);
                } else {
                    stat.put("skipped", stat.get("skipped") + 1);
                }
            } catch (Exception e) {
                log.error("Stock sync failed for product {}: {}", p.getId(), e.getMessage());
                stat.put("failed", stat.get("failed") + 1);
            }
        }
        log.info("Bulk stock sync complete: total={}, updated={}, skipped={}, failed={}",
                stat.get("total"), stat.get("updated"), stat.get("skipped"), stat.get("failed"));
        return stat;
    }

    /**
     * 将 WooCommerce stock_status (instock/outofstock/onbackorder) 转为本地 IN_STOCK/OUT_OF_STOCK/ON_BACKORDER
     */
    private String convertWooStockStatus(String wcStatus) {
        if (wcStatus == null) return "IN_STOCK";
        return switch (wcStatus.toLowerCase()) {
            case "instock" -> "IN_STOCK";
            case "outofstock" -> "OUT_OF_STOCK";
            case "onbackorder" -> "ON_BACKORDER";
            default -> "IN_STOCK";
        };
    }

    // ============ Helpers ============

    /**
     * 安全解析 attributes JSON 字符串为 Map
     */
    private Map<String, Object> parseAttributes(String attributesJson) {
        if (attributesJson == null || attributesJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(attributesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> buildProductData(ProductEntity product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", product.getName());
        if (product.getDetail() != null) {
            data.put("description", product.getDetail());
        }
        // 简短描述 → WooCommerce short_description
        if (product.getShortDetail() != null) {
            data.put("short_description", product.getShortDetail());
        }
        if (product.getOriginalPrice() != null) {
            data.put("regular_price", product.getOriginalPrice().toPlainString());
        }
        if (product.getPrice() != null) {
            data.put("sale_price", product.getPrice().toPlainString());
        }
        if (product.getStock() != null) {
            data.put("stock_quantity", product.getStock());
            data.put("manage_stock", true);
        }
        // 库存管理状态
        if (product.getManageStock() != null) {
            data.put("manage_stock", product.getManageStock());
        }
        // 库存状态
        if (product.getStockStatus() != null) {
            data.put("stock_status", product.getStockStatus().toLowerCase().replace("_", ""));
        }
        // 重量
        if (product.getWeight() != null) {
            data.put("weight", product.getWeight().toPlainString());
        }
        // 尺寸
        Map<String, Object> attrs = parseAttributes(product.getAttributes());
        if (attrs.containsKey("dimensions")) {
            data.put("dimensions", attrs.get("dimensions"));
        }
        // 产品类型
        if (product.getProductType() != null) {
            data.put("type", product.getProductType());
        }
        // 标签
        if (product.getTags() != null && !product.getTags().isBlank()) {
            String[] tagNames = product.getTags().split(",");
            java.util.List<Map<String, Object>> tagList = new java.util.ArrayList<>();
            for (String tn : tagNames) {
                String trimmed = tn.trim();
                if (!trimmed.isEmpty()) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("name", trimmed);
                    tagList.add(t);
                }
            }
            data.put("tags", tagList);
        }
        // 图片：WC 服务端需要绝对 URL 才能下载；本地 /uploads 相对路径会被 WC 拒绝（URL 无效）。
        // dev 阶段先不传图片，商品入库后可手动到 WP 后台上传图，或后续接入"先上传到 WC 再引用 URL"的流程。
        if (product.getMainImage() != null && !product.getMainImage().isBlank()
                && (product.getMainImage().startsWith("http://") || product.getMainImage().startsWith("https://"))) {
            Map<String, Object> img = new LinkedHashMap<>();
            img.put("src", product.getMainImage());
            data.put("images", List.of(img));
        }
        // SKU
        if (product.getSpuCode() != null && !product.getSpuCode().isBlank()) {
            data.put("sku", product.getSpuCode());
        }
        // 状态：onSale=true -> publish, false -> draft
        data.put("status", Boolean.TRUE.equals(product.getOnSale()) ? "publish" : "draft");
        data.put("catalog_visibility", "visible");

        // 分类（如果本地有 categoryId 则附带）
        if (product.getCategoryId() != null) {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("id", product.getCategoryId());
            data.put("categories", List.of(cat));
        }
        return data;
    }

    private Map<String, Object> buildOrderData(OrderEntity order) {
        Map<String, Object> data = new LinkedHashMap<>();

        // Customer
        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("first_name", order.getReceiverName());
        billing.put("phone", order.getReceiverPhone());
        billing.put("address_1", order.getReceiverAddress());
        billing.put("postcode", order.getReceiverZip());
        data.put("billing", billing);

        Map<String, Object> shipping = new LinkedHashMap<>(billing);
        data.put("shipping", shipping);

        // 状态映射
        String status = switch (order.getStatus()) {
            case "PENDING_PAY" -> "pending";
            case "PENDING_SHIP" -> "processing";
            case "SHIPPED", "PENDING_RECEIVE" -> "completed";
            case "CANCELLED" -> "cancelled";
            case "REFUNDING" -> "refunded";
            default -> "pending";
        };
        data.put("status", status);

        // 行项目
        List<Map<String, Object>> lineItems = new ArrayList<>();
        List<OrderItemEntity> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItemEntity>()
                        .eq(OrderItemEntity::getOrderId, order.getId()));
        for (OrderItemEntity item : items) {
            Map<String, Object> li = new LinkedHashMap<>();
            li.put("product_id", item.getProductId());
            li.put("quantity", item.getQuantity());
            li.put("price", item.getPrice().toString());
            li.put("name", item.getProductName());
            lineItems.add(li);
        }
        data.put("line_items", lineItems);

        data.put("customer_note", "Synced from MOYUYO app. Order #" + order.getOrderNo());
        data.put("set_paid", true);

        return data;
    }
}

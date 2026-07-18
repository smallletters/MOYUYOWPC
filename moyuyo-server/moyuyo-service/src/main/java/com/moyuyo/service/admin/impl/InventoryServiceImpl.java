package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.admin.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存管理服务实现
 */
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

  private final ProductSkuMapper productSkuMapper;
  private final ProductMapper productMapper;

  /** 安全库存阈值 */
  private static final int SAFETY_STOCK_THRESHOLD = 20;

  @Override
  public Map<String, Object> getInventoryOverview() {
    try {
      // 查询所有SKU数量
      Long totalSku = productSkuMapper.selectCount(new LambdaQueryWrapper<>());

      // 查询低库存SKU（库存小于阈值的）
      List<ProductSkuEntity> lowStockSkus = productSkuMapper.selectList(
        new LambdaQueryWrapper<ProductSkuEntity>()
          .lt(ProductSkuEntity::getStock, SAFETY_STOCK_THRESHOLD));

      // 批量查询对应的商品名称
      Set<Long> productIds = lowStockSkus.stream()
        .map(ProductSkuEntity::getProductId)
        .collect(Collectors.toSet());

      // 构建商品ID -> 商品名称的映射
      Map<Long, String> productNameMap = new HashMap<>();
      if (!productIds.isEmpty()) {
        List<ProductEntity> products = productMapper.selectBatchIds(productIds);
        productNameMap = products.stream()
          .collect(Collectors.toMap(ProductEntity::getId, ProductEntity::getName));
      }

      // 构建预警列表
      List<Map<String, Object>> alerts = new ArrayList<>();
      for (ProductSkuEntity sku : lowStockSkus) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("sku", sku.getSkuCode());
        alert.put("name", productNameMap.getOrDefault(sku.getProductId(), "未知商品"));
        alert.put("stock", sku.getStock());
        alert.put("threshold", SAFETY_STOCK_THRESHOLD);
        alerts.add(alert);
      }

      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("totalSku", totalSku);
      overview.put("lowStockCount", (long) lowStockSkus.size());
      overview.put("expiringBatchCount", 0);
      overview.put("inTransitCount", 0);
      overview.put("alerts", alerts);
      return overview;
    } catch (Exception e) {
      // 异常时返回空数据，保证API不崩溃
      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("totalSku", 0);
      overview.put("lowStockCount", 0);
      overview.put("expiringBatchCount", 0);
      overview.put("inTransitCount", 0);
      overview.put("alerts", Collections.emptyList());
      return overview;
    }
  }

  @Override
  public List<Map<String, Object>> getAlertList() {
    try {
      // 查询低库存SKU（库存小于阈值的）
      List<ProductSkuEntity> lowStockSkus = productSkuMapper.selectList(
        new LambdaQueryWrapper<ProductSkuEntity>()
          .lt(ProductSkuEntity::getStock, SAFETY_STOCK_THRESHOLD));

      // 批量查询对应的商品名称
      Set<Long> productIds = lowStockSkus.stream()
        .map(ProductSkuEntity::getProductId)
        .collect(Collectors.toSet());

      Map<Long, String> productNameMap = new HashMap<>();
      if (!productIds.isEmpty()) {
        List<ProductEntity> products = productMapper.selectBatchIds(productIds);
        productNameMap = products.stream()
          .collect(Collectors.toMap(ProductEntity::getId, ProductEntity::getName));
      }

      List<Map<String, Object>> list = new ArrayList<>();
      for (ProductSkuEntity sku : lowStockSkus) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sku", sku.getSkuCode());
        item.put("name", productNameMap.getOrDefault(sku.getProductId(), "未知商品"));
        item.put("detail", String.valueOf(sku.getStock()));
        item.put("type", "LOW_STOCK");
        list.add(item);
      }
      return list;
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}

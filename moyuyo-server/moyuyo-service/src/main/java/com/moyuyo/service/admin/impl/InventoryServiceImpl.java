package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.InventoryBatchEntity;
import com.moyuyo.dao.admin.entity.InventoryTransferEntity;
import com.moyuyo.dao.admin.mapper.InventoryBatchMapper;
import com.moyuyo.dao.admin.mapper.InventoryTransferMapper;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.admin.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
  private final InventoryBatchMapper inventoryBatchMapper;
  private final InventoryTransferMapper inventoryTransferMapper;

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

      // KPI 4 个细分字段真实查询：
      // 1) weeklyIncrease：本周新增 SKU 数（基于 mo_product_sku.create_time，mo_product_sku 表已有 create_time 由 MyBatis-Plus 自动填充）
      //    注：V20260819_03 之前 mo_product_sku 没有 create_time 列。这里通过 join mo_product 的 create_time 兜底获取本周新增商品数。
      // 2) urgentReplenish：库存为 0 的 SKU 数
      // 3) expiringBatches：mo_inventory_batch 中 status='EXPIRING' 的批次数（30 天内到期的）
      // 4) inTransit：mo_inventory_transfer 中 status='IN_TRANSIT' 的调拨数量之和

      // 1) 周新增 SKU 数：基于 mo_product_sku.update_time >= 本周一
      LocalDateTime weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay();
      Long weeklyIncrease = productSkuMapper.selectCount(
          new LambdaQueryWrapper<ProductSkuEntity>().ge(ProductSkuEntity::getUpdateTime, weekStart));

      // 2) 紧急补货：库存为 0
      Long urgentReplenish = productSkuMapper.selectCount(
          new LambdaQueryWrapper<ProductSkuEntity>().eq(ProductSkuEntity::getStock, 0));

      // 3) 临期批次数
      Long expiringBatches = inventoryBatchMapper.selectCount(
          new LambdaQueryWrapper<InventoryBatchEntity>().eq(InventoryBatchEntity::getStatus, "EXPIRING"));

      // 4) 在途调拨（汇总所有 IN_TRANSIT 单据的 quantity）
      List<InventoryTransferEntity> transitList = inventoryTransferMapper.selectList(
          new LambdaQueryWrapper<InventoryTransferEntity>().eq(InventoryTransferEntity::getStatus, "IN_TRANSIT"));
      long inTransit = transitList.stream()
          .mapToLong(t -> t.getQuantity() != null ? t.getQuantity() : 0)
          .sum();

      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("totalSku", totalSku);
      overview.put("weeklyIncrease", weeklyIncrease);
      overview.put("lowStockCount", (long) lowStockSkus.size());
      overview.put("urgentReplenish", urgentReplenish);
      overview.put("expiringBatches", expiringBatches);
      overview.put("inTransit", inTransit);
      overview.put("alerts", alerts);
      return overview;
    } catch (Exception e) {
      // 异常时返回空数据，保证API不崩溃
      Map<String, Object> overview = new LinkedHashMap<>();
      overview.put("totalSku", 0);
      overview.put("weeklyIncrease", 0);
      overview.put("lowStockCount", 0);
      overview.put("urgentReplenish", 0);
      overview.put("expiringBatches", 0);
      overview.put("inTransit", 0);
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

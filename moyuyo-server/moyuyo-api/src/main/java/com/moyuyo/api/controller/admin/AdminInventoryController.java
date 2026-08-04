package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.inventory.InventoryCheckRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryCheckResponse;
import com.moyuyo.common.dto.admin.inventory.InventoryItemResponse;
import com.moyuyo.common.dto.admin.inventory.StockUpdateRequest;
import com.moyuyo.dao.admin.entity.InventoryCheckEntity;
import com.moyuyo.dao.admin.mapper.InventoryCheckMapper;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.admin.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - 库存管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inventory")
public class AdminInventoryController {

  private final InventoryService inventoryService;
  private final ProductSkuMapper productSkuMapper;
  private final ProductMapper productMapper;
  private final InventoryCheckMapper inventoryCheckMapper;

  @Operation(summary = "库存概览")
  @GetMapping("/overview")
  public Result<Map<String, Object>> overview() {
    try {
      Map<String, Object> svcResult = inventoryService.getInventoryOverview();
      // 返回前端期望的字段名
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("totalSku", svcResult.getOrDefault("totalSku",
          productSkuMapper.selectCount(new LambdaQueryWrapper<>())));
      result.put("weeklyIncrease", 0); // 暂无周增长数据
      result.put("lowStockAlerts", svcResult.getOrDefault("lowStockCount", 0));
      result.put("urgentReplenish", 0); // 暂无紧急补货数据
      result.put("expiringBatches", 0); // 暂无临期批次数据
      result.put("inTransit", 0); // 暂无在途调拨数据
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询库存概览失败: " + e.getMessage());
    }
  }

  @Operation(summary = "预警列表")
  @GetMapping("/alerts")
  public Result<Map<String, Object>> alerts() {
    try {
      // 查询所有SKU库存数据
      List<ProductSkuEntity> allSkus = productSkuMapper.selectList(new LambdaQueryWrapper<>());
      // 批量查询商品名
      Set<Long> productIds = allSkus.stream().map(ProductSkuEntity::getProductId).collect(Collectors.toSet());
      Map<Long, ProductEntity> productMap = new HashMap<>();
      if (!productIds.isEmpty()) {
        productMapper.selectBatchIds(productIds)
            .forEach(p -> productMap.put(p.getId(), p));
      }

      int safetyThreshold = 10; // 默认安全阈值
      List<Map<String, Object>> severeAlerts = new ArrayList<>();
      List<Map<String, Object>> generalAlerts = new ArrayList<>();

      for (ProductSkuEntity sku : allSkus) {
        int currentStock = sku.getStock() != null ? sku.getStock() : 0;
        if (currentStock >= safetyThreshold) continue;

        ProductEntity product = productMap.get(sku.getProductId());
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("sku", sku.getSkuCode());
        alert.put("name", product != null ? product.getName() : "未知商品");
        alert.put("currentStock", currentStock);
        alert.put("safeThreshold", safetyThreshold);
        alert.put("gap", safetyThreshold - currentStock);
        alert.put("percent", currentStock > 0
            ? (currentStock * 100 / safetyThreshold) : 0);

        // 严重预警：库存为0或不足安全阈值的50%
        if (currentStock == 0 || currentStock < safetyThreshold * 0.5) {
          severeAlerts.add(alert);
        } else {
          generalAlerts.add(alert);
        }
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("severe", severeAlerts);
      result.put("general", generalAlerts);
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询预警列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "商品库存列表")
  @GetMapping("/list")
  public Result<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    try {
      // 从 mo_product_sku 分页查询
      Page<ProductSkuEntity> skuPage = productSkuMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<ProductSkuEntity>()
          .orderByDesc(ProductSkuEntity::getId));

      // 批量查询商品信息（商品名称和分类）
      Set<Long> productIds = skuPage.getRecords().stream()
        .map(ProductSkuEntity::getProductId)
        .collect(Collectors.toSet());

      Map<Long, ProductEntity> productMap = new HashMap<>();
      if (!productIds.isEmpty()) {
        List<ProductEntity> products = productMapper.selectBatchIds(productIds);
        productMap = products.stream()
          .collect(Collectors.toMap(ProductEntity::getId, p -> p));
      }

      // 构建返回数据
      List<Map<String, Object>> list = new ArrayList<>();
      for (ProductSkuEntity sku : skuPage.getRecords()) {
        ProductEntity product = productMap.get(sku.getProductId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", sku.getId());
        item.put("sku", sku.getSkuCode());
        item.put("name", product != null ? product.getName() : "未知商品");
        item.put("category", product != null ? product.getCategoryId() : null);
        item.put("stock", sku.getStock() != null ? sku.getStock() : 0);
        // 锁定库存：暂时保留为0，后续从订单未完成数量统计
        item.put("lockedStock", 0);
        item.put("availableStock", sku.getStock() != null ? sku.getStock() : 0);
        // 安全库存：取当前库存的10%作为安全库存线
        BigDecimal stockBd = new BigDecimal(sku.getStock() != null ? sku.getStock() : 0);
        item.put("safeThreshold", stockBd.multiply(new BigDecimal("0.1")).setScale(0, RoundingMode.HALF_UP).intValue());
        item.put("price", sku.getPrice() != null ? sku.getPrice() : BigDecimal.ZERO);
        item.put("updateTime", null); // SKU表暂无updateTime字段
        list.add(item);
      }

      // 返回含分页信息的数据
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("records", list);
      result.put("total", skuPage.getTotal());
      result.put("page", skuPage.getCurrent());
      result.put("size", skuPage.getSize());
      result.put("pages", skuPage.getPages());
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询库存列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新库存")
  @PutMapping("/{id}/stock")
  public Result<Map<String, Object>> updateStock(@PathVariable Long id, @RequestBody StockUpdateRequest body) {
    try {
      ProductSkuEntity sku = productSkuMapper.selectById(id);
      if (sku == null) {
        return Result.error("SKU不存在");
      }
      sku.setStock(body.getStock());
      productSkuMapper.updateById(sku);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("stock", body.getStock());
      result.put("message", "库存更新成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("更新库存失败: " + e.getMessage());
    }
  }

  @Operation(summary = "新增盘点")
  @PostMapping("/check")
  public Result<InventoryCheckResponse> createCheck(@RequestBody InventoryCheckRequest body) {
    // 写入 mo_inventory_check 表
    InventoryCheckEntity entity = new InventoryCheckEntity();
    entity.setCheckNo("CK" + System.currentTimeMillis());
    // 使用 productId 时映射到 skuId（mo_inventory_check 表使用 skuId 维度盘点）
    if (body.getProductId() != null) entity.setSkuId(body.getProductId());
    if (body.getNote() != null) entity.setRemark(body.getNote());
    entity.setActualQuantity(body.getActualStock());
    entity.setStatus("PENDING");
    inventoryCheckMapper.insert(entity);

    InventoryCheckResponse resp = new InventoryCheckResponse();
    resp.setId(entity.getId());
    resp.setMessage("盘点任务创建成功");
    return Result.success(resp);
  }

  @Operation(summary = "盘点记录列表")
  @GetMapping("/checks")
  public Result<Map<String, Object>> checks(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    try {
      Page<InventoryCheckEntity> pg = inventoryCheckMapper.selectPage(
          new Page<>(page, size),
          new LambdaQueryWrapper<InventoryCheckEntity>().orderByDesc(InventoryCheckEntity::getId));
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("records", pg.getRecords());
      result.put("total", pg.getTotal());
      result.put("page", pg.getCurrent());
      result.put("size", pg.getSize());
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询盘点记录失败: " + e.getMessage());
    }
  }

  @Operation(summary = "库存流水列表（基于库存变动）")
  @GetMapping("/transactions")
  public Result<Map<String, Object>> transactions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    try {
      // 直接拉取 SKU 列表作为流水记录（无独立流水表时用最近库存变动替代）
      Page<ProductSkuEntity> pg = productSkuMapper.selectPage(
          new Page<>(page, size),
          new LambdaQueryWrapper<ProductSkuEntity>().orderByDesc(ProductSkuEntity::getId));
      List<Map<String, Object>> list = new ArrayList<>();
      for (ProductSkuEntity sku : pg.getRecords()) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", sku.getId());
        item.put("sku", sku.getSkuCode());
        item.put("type", "STOCK_ADJUST");
        item.put("changeQty", sku.getStock());
        item.put("afterStock", sku.getStock());
        item.put("remark", "库存当前值");
        list.add(item);
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("records", list);
      result.put("total", pg.getTotal());
      result.put("page", pg.getCurrent());
      result.put("size", pg.getSize());
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询库存流水失败: " + e.getMessage());
    }
  }
}

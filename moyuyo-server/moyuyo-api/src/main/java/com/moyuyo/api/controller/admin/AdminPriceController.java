package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.PriceStrategyEntity;
import com.moyuyo.dao.admin.mapper.OrderPriceModifyMapper;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.admin.AdminPriceStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 价格管理")
@RestController
@RequestMapping("/api/admin/price")
@RequiredArgsConstructor
public class AdminPriceController {

  private final AdminPriceStrategyService adminPriceStrategyService;
  private final ProductMapper productMapper;
  private final OrderPriceModifyMapper priceModifyMapper;

  // ==================== 商品定价列表（供 PriceManage.vue 使用） ====================

  @Operation(summary = "商品定价列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    // 从产品表查询，优先支持关键词搜索（兼容 PriceManage.vue 的调用方式）
    LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<>();
    if (keyword != null && !keyword.isEmpty()) {
      wrapper.like(ProductEntity::getName, keyword);
    }
    wrapper.orderByDesc(ProductEntity::getCreateTime);

    Page<ProductEntity> entityPage = productMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(p -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", p.getId());
      item.put("productName", p.getName());
      item.put("sku", p.getSpuCode() != null ? p.getSpuCode() : "");
      item.put("originalPrice", p.getOriginalPrice() != null ? p.getOriginalPrice() : p.getPrice());
      item.put("sellingPrice", p.getPrice());
      item.put("costPrice", BigDecimal.ZERO); // 成本价后续可扩展
      // 计算毛利率
      BigDecimal price = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
      BigDecimal origPrice = p.getOriginalPrice() != null ? p.getOriginalPrice() : price;
      if (price.compareTo(BigDecimal.ZERO) > 0) {
        item.put("margin", origPrice.subtract(price).divide(price, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP));
      } else {
        item.put("margin", BigDecimal.ZERO);
      }
      item.put("lastModifyTime", p.getUpdateTime() != null ? p.getUpdateTime() : p.getCreateTime());
      return item;
    }).toList());
    return Result.success(resultPage);
  }

  @Operation(summary = "调整商品价格")
  @PostMapping("/create")
  @Transactional
  public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    // 前端发送: { productName, sku, sellingPrice, costPrice, reason }
    String productName = (String) body.get("productName");
    String sku = (String) body.get("sku");
    BigDecimal sellingPrice = body.get("sellingPrice") != null
        ? new BigDecimal(body.get("sellingPrice").toString()) : null;
    String reason = (String) body.getOrDefault("reason", "");

    // 查找产品
    ProductEntity product = null;
    if (sku != null && !sku.isEmpty()) {
      product = productMapper.selectOne(
          new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getSpuCode, sku));
    }
    if (product == null && productName != null && !productName.isEmpty()) {
      product = productMapper.selectOne(
          new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getName, productName));
    }
    if (product == null) {
      return Result.error(404, "未找到对应商品");
    }

    // 保存旧价格（用于记录历史）
    BigDecimal oldPrice = product.getPrice();

    // 更新产品价格
    if (sellingPrice != null) {
      product.setPrice(sellingPrice);
      if (product.getOriginalPrice() == null) {
        product.setOriginalPrice(oldPrice);
      }
      product.setUpdateTime(LocalDateTime.now());
      productMapper.updateById(product);
    }

    // 记录价格调整历史
    recordPriceHistory(product, oldPrice, sellingPrice, reason, "系统");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", product.getId());
    result.put("message", "价格调整成功");
    return Result.success(result);
  }

  @Operation(summary = "更新商品价格")
  @PutMapping("/update")
  @Transactional
  public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
    // 前端发送: { id, sellingPrice, costPrice, reason }
    Long id = body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null;
    BigDecimal sellingPrice = body.get("sellingPrice") != null
        ? new BigDecimal(body.get("sellingPrice").toString()) : null;
    String reason = (String) body.getOrDefault("reason", "");

    if (id == null) {
      return Result.error(400, "参数错误：id 不能为空");
    }

    ProductEntity product = productMapper.selectById(id);
    if (product == null) {
      return Result.error(404, "商品不存在: " + id);
    }

    BigDecimal oldPrice = product.getPrice();

    if (sellingPrice != null) {
      product.setPrice(sellingPrice);
      if (product.getOriginalPrice() == null) {
        product.setOriginalPrice(oldPrice);
      }
      product.setUpdateTime(LocalDateTime.now());
      productMapper.updateById(product);
    }

    recordPriceHistory(product, oldPrice, sellingPrice, reason, "系统");

    return Result.success(Map.of("id", id, "message", "价格更新成功"));
  }

  @Operation(summary = "删除策略（兼容旧接口）")
  @DeleteMapping("/{id}")
  public Result<Map<String, Object>> delete(@PathVariable Long id) {
    adminPriceStrategyService.delete(id);
    return Result.success(Map.of("id", id, "message", "删除成功"));
  }

  @Operation(summary = "启用/禁用策略（兼容旧接口）")
  @PutMapping("/{id}/toggle")
  public Result<Map<String, Object>> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Boolean enabled = Boolean.valueOf(String.valueOf(body.getOrDefault("enabled", true)));
    adminPriceStrategyService.toggle(id, enabled);
    return Result.success(Map.of("id", id, "enabled", enabled, "message", "状态更新成功"));
  }

  // ==================== 价格历史（供 PriceHistory.vue 使用） ====================

  @Operation(summary = "价格历史列表")
  @GetMapping("/history")
  public Result<?> history(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    // 从订单改价表查询价格调整记录
    LambdaQueryWrapper<com.moyuyo.dao.admin.entity.OrderPriceModifyEntity> wrapper =
        new LambdaQueryWrapper<>();
    wrapper.orderByDesc(com.moyuyo.dao.admin.entity.OrderPriceModifyEntity::getCreateTime);

    Page<com.moyuyo.dao.admin.entity.OrderPriceModifyEntity> entityPage =
        priceModifyMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("productName", ""); // 订单改价记录中没有直接存商品名
      item.put("sku", e.getOrderNo());
      item.put("originalPrice", e.getOriginalAmount());
      item.put("adjustedPrice", e.getFinalAmount());
      item.put("adjustType", e.getAdjustAmount().compareTo(BigDecimal.ZERO) >= 0 ? "涨价" : "降价");
      item.put("operator", e.getOperator());
      item.put("adjustTime", e.getCreateTime());
      return item;
    }).toList());

    // 如果有关键词过滤（前端自己也会做过滤）
    if (keyword != null && !keyword.isEmpty()) {
      String kw = keyword.toLowerCase();
      resultPage.setRecords(resultPage.getRecords().stream()
          .filter(m -> String.valueOf(m.getOrDefault("sku", "")).toLowerCase().contains(kw))
          .toList());
      resultPage.setTotal(resultPage.getRecords().size());
    }

    return Result.success(resultPage);
  }

  /**
   * 记录价格调整历史
   */
  private void recordPriceHistory(ProductEntity product, BigDecimal oldPrice,
                                   BigDecimal newPrice, String reason, String operator) {
    if (oldPrice == null || newPrice == null || oldPrice.compareTo(newPrice) == 0) {
      return; // 价格未变化，不记录
    }
    // 写入订单改价表作为价格历史（也可后续独立建表）
    com.moyuyo.dao.admin.entity.OrderPriceModifyEntity history =
        new com.moyuyo.dao.admin.entity.OrderPriceModifyEntity();
    history.setOrderId(product.getId());
    history.setOrderNo(product.getSpuCode() != null ? product.getSpuCode() : product.getName());
    history.setOriginalAmount(oldPrice);
    history.setFinalAmount(newPrice);
    history.setAdjustAmount(newPrice.subtract(oldPrice));
    history.setReason(reason != null && !reason.isEmpty() ? reason : "价格调整");
    history.setReasonType("MANUAL");
    history.setOperator(operator);
    history.setStatus("APPROVED");
    history.setCreateTime(LocalDateTime.now());
    priceModifyMapper.insert(history);
  }
}

package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.FlashSaleOrderEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.FlashSaleOrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.admin.AdminFlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 限时抢购服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminFlashSaleServiceImpl implements AdminFlashSaleService {

  private final FlashSaleMapper flashSaleMapper;
  private final FlashSaleOrderMapper flashSaleOrderMapper;
  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;

  @Override
  public List<Map<String, Object>> listAll() {
    List<FlashSaleEntity> list = flashSaleMapper.selectList(
        new LambdaQueryWrapper<FlashSaleEntity>().orderByDesc(f -> f.getCreateTime()));
    return toItemsWithProduct(list);
  }

  @Override
  public Map<String, Object> listPage(int page, int size) {
    Page<FlashSaleEntity> pageObj = new Page<>(page, size);
    Page<FlashSaleEntity> result = flashSaleMapper.selectPage(pageObj,
        new LambdaQueryWrapper<FlashSaleEntity>().orderByDesc(f -> f.getCreateTime()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("total", result.getTotal());
    data.put("records", toItemsWithProduct(result.getRecords()));
    return data;
  }

  /** 将闪购实体转为前端展示用Map */
  private Map<String, Object> toItem(FlashSaleEntity f) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", f.getId());
    item.put("name", f.getName());
    item.put("productId", f.getProductId());
    item.put("skuId", f.getSkuId());
    item.put("status", f.getActive());
    item.put("startTime", f.getStartTime());
    item.put("endTime", f.getEndTime());
    item.put("flashPrice", f.getFlashPrice());
    item.put("originalPrice", f.getOriginalPrice());
    item.put("stock", f.getTotalStock());
    item.put("createTime", f.getCreateTime());
    return item;
  }

  /**
   * 批量按 productId 补齐商品名/spuCode 后再转换。
   * 用于列表/分页等"多条秒杀"场景，避免对每条记录都发起额外查询。
   */
  private List<Map<String, Object>> toItemsWithProduct(List<FlashSaleEntity> rows) {
    if (rows == null || rows.isEmpty()) return java.util.Collections.emptyList();
    // 1) 收集不重复的 productId
    java.util.Set<Long> productIds = new java.util.HashSet<>();
    for (FlashSaleEntity f : rows) {
      if (f.getProductId() != null) productIds.add(f.getProductId());
    }
    // 2) 一次性批量查询商品，构造 productId -> ProductEntity 映射
    java.util.Map<Long, ProductEntity> productMap = java.util.Collections.emptyMap();
    if (!productIds.isEmpty()) {
      // selectBatchIds 在 MyBatis-Plus 新版本中已标记 deprecated，
      // 改用链式 lambdaQuery().in().list() 等价且无警告
      List<ProductEntity> products = productMapper.selectList(
          new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductEntity>()
              .in(p -> p.getId(), productIds));
      productMap = products.stream()
          .collect(Collectors.toMap(p -> p.getId(), p -> p, (a, b) -> a));
    }
    // 3) 转换时附带商品名/spuCode
    List<Map<String, Object>> items = new java.util.ArrayList<>(rows.size());
    for (FlashSaleEntity f : rows) {
      Map<String, Object> item = toItem(f);
      ProductEntity p = f.getProductId() != null ? productMap.get(f.getProductId()) : null;
      item.put("productName", p != null ? p.getName() : null);
      item.put("spuCode", p != null ? p.getSpuCode() : null);
      items.add(item);
    }
    return items;
  }

  @Override
  @Transactional
  public void create(Map<String, Object> data) {
    FlashSaleEntity entity = new FlashSaleEntity();
    // 兼容 snake_case 字段命名（前端部分接口使用下划线）
    String name = strVal(data, "name");
    if (name == null) name = strVal(data, "flash_name");
    if (name == null) name = "限时抢购_" + System.currentTimeMillis();
    entity.setName(name);

    Object productIdVal = data.get("productId");
    if (productIdVal == null) productIdVal = data.get("product_id");
    if (productIdVal != null) {
      Long pid = Long.valueOf(productIdVal.toString());
      // 商品存在性校验：避免脏数据写入，便于排查"活动上架了但 APP 找不到商品"
      if (productMapper.selectById(pid) == null) {
        throw new IllegalArgumentException("商品不存在 (productId=" + pid + ")");
      }
      entity.setProductId(pid);
    } else {
      entity.setProductId(0L);
    }

    Object skuIdVal = data.get("skuId");
    if (skuIdVal == null) skuIdVal = data.get("sku_id");
    if (skuIdVal != null) {
      Long skuId = Long.valueOf(skuIdVal.toString());
      // SKU 存在性 + 归属校验：SKU 必须存在且属于上面设置的 productId
      validateSkuBelongsToProduct(skuId, entity.getProductId());
      entity.setSkuId(skuId);
    }

    Object flashPriceVal = data.get("flashPrice");
    if (flashPriceVal == null) flashPriceVal = data.get("flash_price");
    if (flashPriceVal != null) entity.setFlashPrice(new BigDecimal(flashPriceVal.toString()));
    else entity.setFlashPrice(BigDecimal.ZERO);

    Object originalPriceVal = data.get("originalPrice");
    if (originalPriceVal == null) originalPriceVal = data.get("original_price");
    if (originalPriceVal != null) entity.setOriginalPrice(new BigDecimal(originalPriceVal.toString()));
    else entity.setOriginalPrice(BigDecimal.ZERO);

    Object stockVal = data.get("stock");
    if (stockVal == null) stockVal = data.get("total_stock");
    if (stockVal != null) entity.setTotalStock(Integer.valueOf(stockVal.toString()));
    else entity.setTotalStock(0);

    if (data.get("startTime") != null) {
      String ts = data.get("startTime").toString().trim();
      if (!ts.isEmpty()) entity.setStartTime(parseTime(ts));
    }
    if (data.get("endTime") != null) {
      String ts = data.get("endTime").toString().trim();
      if (!ts.isEmpty()) entity.setEndTime(parseTime(ts));
    }
    // 兜底：start_time/end_time 是 NOT NULL 必填字段
    if (entity.getStartTime() == null) entity.setStartTime(LocalDateTime.now());
    if (entity.getEndTime() == null) entity.setEndTime(LocalDateTime.now().plusDays(1));
    entity.setActive(true);
    flashSaleMapper.insert(entity);
    // 将生成的主键回写到请求数据，方便控制器返回真实ID
    data.put("id", entity.getId());
  }

  private String strVal(Map<String, Object> data, String key) {
    Object v = data.get(key);
    return v == null ? null : v.toString();
  }

  /**
   * 校验 SKU 存在且属于指定商品。
   * - skuId 不存在 → IllegalArgumentException("SKU 不存在")
   * - skuId 存在但不属于 productId → IllegalArgumentException("SKU 不属于该商品")
   * - productId 为 null/0 → 跳过（视为商品级秒杀，不校验 SKU 归属）
   */
  private void validateSkuBelongsToProduct(Long skuId, Long productId) {
    if (skuId == null) return;
    // productId 为 0L 表示"商品级秒杀"，允许任意 SKU（理论上不会出现 SKU，因 create 时 productId=0 也无法选 SKU）
    if (productId == null || productId == 0L) return;
    ProductSkuEntity sku = productSkuMapper.selectById(skuId);
    if (sku == null) {
      throw new IllegalArgumentException("SKU 不存在 (skuId=" + skuId + ")");
    }
    if (sku.getProductId() == null || !sku.getProductId().equals(productId)) {
      throw new IllegalArgumentException(
          "SKU 不属于该商品 (skuId=" + skuId + ", expectedProductId=" + productId
              + ", actualProductId=" + sku.getProductId() + ")");
    }
  }

  @Override
  @Transactional
  public void update(Map<String, Object> data) {
    if (data.get("id") == null) return;
    FlashSaleEntity entity = flashSaleMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    if (data.get("productId") != null) {
      Long pid = Long.valueOf(data.get("productId").toString());
      // 商品存在性校验：避免脏数据写入
      if (productMapper.selectById(pid) == null) {
        throw new IllegalArgumentException("商品不存在 (productId=" + pid + ")");
      }
      // productId 变更后，已绑定的 skuId 可能不再属于新商品
      // 必须先用"新 productId"重新校验当前 skuId，否则会写入 productId/skuId 归属不一致的脏数据
      if (entity.getSkuId() != null) {
        validateSkuBelongsToProduct(entity.getSkuId(), pid);
      }
      entity.setProductId(pid);
    }
    // skuId 支持显式传 null 清空，也支持 camelCase / snake_case 兜底；
    // 校验时基于当前 entity.productId（可能来自本次入参，也可能保留旧值）
    if (data.containsKey("skuId")) {
      Object skuVal = data.get("skuId");
      if (skuVal == null) {
        entity.setSkuId(null);
      } else {
        Long skuId = Long.valueOf(skuVal.toString());
        validateSkuBelongsToProduct(skuId, entity.getProductId());
        entity.setSkuId(skuId);
      }
    } else if (data.containsKey("sku_id")) {
      Object skuVal = data.get("sku_id");
      if (skuVal == null) {
        entity.setSkuId(null);
      } else {
        Long skuId = Long.valueOf(skuVal.toString());
        validateSkuBelongsToProduct(skuId, entity.getProductId());
        entity.setSkuId(skuId);
      }
    }
    if (data.get("flashPrice") != null) entity.setFlashPrice(new BigDecimal(data.get("flashPrice").toString()));
    if (data.get("originalPrice") != null) entity.setOriginalPrice(new BigDecimal(data.get("originalPrice").toString()));
    if (data.get("stock") != null) entity.setTotalStock(Integer.valueOf(data.get("stock").toString()));
    if (data.get("startTime") != null) {
      String ts = data.get("startTime").toString().trim();
      if (!ts.isEmpty()) entity.setStartTime(parseTime(ts));
    }
    if (data.get("endTime") != null) {
      String ts = data.get("endTime").toString().trim();
      if (!ts.isEmpty()) entity.setEndTime(parseTime(ts));
    }
    flashSaleMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void delete(Long id) {
    // 先删除关联的抢购订单，再删除抢购活动
    flashSaleOrderMapper.delete(new LambdaQueryWrapper<FlashSaleOrderEntity>()
        .eq(o -> o.getFlashSaleId(), id));
    flashSaleMapper.deleteById(id);
  }

  @Override
  @Transactional
  public void updateStatus(Long id, String status) {
    FlashSaleEntity entity = flashSaleMapper.selectById(id);
    if (entity != null) {
      entity.setActive("ACTIVE".equals(status) || "UPCOMING".equals(status));
      flashSaleMapper.updateById(entity);
    }
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    
    // 总数
    long totalCount = flashSaleMapper.selectCount(new LambdaQueryWrapper<>());
    
    // 进行中的活动数
    long activeCount = flashSaleMapper.selectCount(
        new LambdaQueryWrapper<FlashSaleEntity>().eq(f -> f.getActive(), true));
    
    // 总参与人数（秒杀订单数）
    long participants = flashSaleOrderMapper.selectCount(new LambdaQueryWrapper<>());
    
    // 计算转化率：参与人数 / (总库存 * 活动数)，保留两位小数
    double conversionRate = 0;
    if (totalCount > 0) {
      List<FlashSaleEntity> allList = flashSaleMapper.selectList(new LambdaQueryWrapper<>());
      int totalStock = allList.stream().mapToInt(f -> f.getTotalStock() != null ? f.getTotalStock() : 0).sum();
      if (totalStock > 0) {
        conversionRate = Math.round(((double) participants / totalStock) * 10000.0) / 100.0;
      }
    }
    
    stats.put("totalCount", totalCount);
    stats.put("activeCount", activeCount);
    stats.put("joinCount", participants);
    stats.put("participants", participants);
    stats.put("conversionRate", conversionRate);
    return stats;
  }

  @Override
  public Map<String, Object> getDetail(Long id) {
    FlashSaleEntity f = flashSaleMapper.selectById(id);
    if (f == null) {
      return null;
    }
    Map<String, Object> item = toItem(f);
    if (f.getProductId() != null) {
      ProductEntity p = productMapper.selectById(f.getProductId());
      if (p != null) {
        item.put("productName", p.getName());
        item.put("spuCode", p.getSpuCode());
      }
    }
    return item;
  }

  /**
   * 安全解析时间字符串，兼容多种格式：
   * 1. ISO 8601 带时区（前端 el-date-picker.toISOString() 形如 2026-08-27T16:00:00.000Z）
   *    → 转为本地时区后再丢给 LocalDateTime
   * 2. ISO_LOCAL_DATE_TIME（2026-08-27T16:00:00 或 2026-08-27T16:00:00.000）
   * 3. yyyy-MM-dd HH:mm:ss（推荐格式，前端已统一）
   */
  private LocalDateTime parseTime(String timeStr) {
    if (timeStr == null || timeStr.isEmpty()) return null;
    String s = timeStr.trim();
    // 1) 带 Z 或 +hh:mm 时区的 ISO 8601：用 Instant + 系统时区
    if (s.endsWith("Z") || s.matches(".*[+-]\\d{2}:?\\d{2}$")) {
      try {
        java.time.Instant inst = java.time.Instant.parse(s);
        return inst.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
      } catch (Exception ignored) {
        // 落到下面的本地解析
      }
    }
    // 2) 纯 ISO_LOCAL_DATE_TIME（无时区）
    try {
      return LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (Exception ignored) {
      // 落到下面
    }
    // 3) 空格分隔的常见格式
    return LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  }
}

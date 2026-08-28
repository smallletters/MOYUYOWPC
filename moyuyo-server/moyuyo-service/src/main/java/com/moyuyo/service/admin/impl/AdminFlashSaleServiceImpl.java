package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.FlashSaleOrderEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.FlashSaleOrderMapper;
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

  @Override
  public List<Map<String, Object>> listAll() {
    List<FlashSaleEntity> list = flashSaleMapper.selectList(
        new LambdaQueryWrapper<FlashSaleEntity>().orderByDesc(FlashSaleEntity::getCreateTime));
    return list.stream().map(this::toItem).collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> listPage(int page, int size) {
    Page<FlashSaleEntity> pageObj = new Page<>(page, size);
    Page<FlashSaleEntity> result = flashSaleMapper.selectPage(pageObj,
        new LambdaQueryWrapper<FlashSaleEntity>().orderByDesc(FlashSaleEntity::getCreateTime));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("total", result.getTotal());
    data.put("records", result.getRecords().stream().map(this::toItem).collect(Collectors.toList()));
    return data;
  }

  /** 将闪购实体转为前端展示用Map */
  private Map<String, Object> toItem(FlashSaleEntity f) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", f.getId());
    item.put("name", f.getName());
    item.put("productId", f.getProductId());
    item.put("status", f.getActive());
    item.put("startTime", f.getStartTime());
    item.put("endTime", f.getEndTime());
    item.put("flashPrice", f.getFlashPrice());
    item.put("originalPrice", f.getOriginalPrice());
    item.put("stock", f.getTotalStock());
    item.put("createTime", f.getCreateTime());
    return item;
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
    if (productIdVal != null) entity.setProductId(Long.valueOf(productIdVal.toString()));
    else entity.setProductId(0L);

    Object skuIdVal = data.get("skuId");
    if (skuIdVal == null) skuIdVal = data.get("sku_id");
    if (skuIdVal != null) entity.setSkuId(Long.valueOf(skuIdVal.toString()));

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

  @Override
  @Transactional
  public void update(Map<String, Object> data) {
    if (data.get("id") == null) return;
    FlashSaleEntity entity = flashSaleMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    if (data.get("productId") != null) entity.setProductId(Long.valueOf(data.get("productId").toString()));
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
        .eq(FlashSaleOrderEntity::getFlashSaleId, id));
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
        new LambdaQueryWrapper<FlashSaleEntity>().eq(FlashSaleEntity::getActive, true));
    
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
    return toItem(f);
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

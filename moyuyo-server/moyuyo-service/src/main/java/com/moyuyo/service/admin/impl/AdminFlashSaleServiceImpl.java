package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
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

  @Override
  public List<Map<String, Object>> listAll() {
    List<FlashSaleEntity> list = flashSaleMapper.selectList(
        new LambdaQueryWrapper<FlashSaleEntity>().orderByDesc(FlashSaleEntity::getCreateTime));
    return list.stream().map(f -> {
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
    }).collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void create(Map<String, Object> data) {
    FlashSaleEntity entity = new FlashSaleEntity();
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
    entity.setActive(true);
    flashSaleMapper.insert(entity);
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

  /**
   * 安全解析时间字符串，兼容 ISO 格式
   */
  private LocalDateTime parseTime(String timeStr) {
    if (timeStr == null || timeStr.isEmpty()) return null;
    try {
      return LocalDateTime.parse(timeStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (Exception e) {
      // 尝试 yyyy-MM-dd HH:mm:ss 格式
      return LocalDateTime.parse(timeStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
  }
}

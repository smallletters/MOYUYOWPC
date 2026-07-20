package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.service.admin.AdminFlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
      item.put("status", f.getActive());
      item.put("startTime", f.getStartTime());
      item.put("endTime", f.getEndTime());
      item.put("flashPrice", f.getFlashPrice());
      item.put("stock", f.getTotalStock());
      item.put("createTime", f.getCreateTime());
      return item;
    }).collect(Collectors.toList());
  }

  @Override
  public void create(Map<String, Object> data) {
    FlashSaleEntity entity = new FlashSaleEntity();
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    if (data.get("flashPrice") != null) entity.setFlashPrice(new BigDecimal(data.get("flashPrice").toString()));
    if (data.get("stock") != null) entity.setTotalStock(Integer.valueOf(data.get("stock").toString()));
    if (data.get("startTime") != null) entity.setStartTime(LocalDateTime.parse(data.get("startTime").toString()));
    if (data.get("endTime") != null) entity.setEndTime(LocalDateTime.parse(data.get("endTime").toString()));
    entity.setActive(true);
    flashSaleMapper.insert(entity);
  }

  @Override
  public void update(Map<String, Object> data) {
    if (data.get("id") == null) return;
    FlashSaleEntity entity = flashSaleMapper.selectById(Long.valueOf(data.get("id").toString()));
    if (entity == null) return;
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    if (data.get("flashPrice") != null) entity.setFlashPrice(new BigDecimal(data.get("flashPrice").toString()));
    if (data.get("stock") != null) entity.setTotalStock(Integer.valueOf(data.get("stock").toString()));
    if (data.get("startTime") != null) entity.setStartTime(LocalDateTime.parse(data.get("startTime").toString()));
    if (data.get("endTime") != null) entity.setEndTime(LocalDateTime.parse(data.get("endTime").toString()));
    flashSaleMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    flashSaleMapper.deleteById(id);
  }

  @Override
  public void updateStatus(Long id, String status) {
    FlashSaleEntity entity = flashSaleMapper.selectById(id);
    if (entity != null) {
      entity.setActive("ACTIVE".equals(status) || "UPCOMING".equals(status));
      flashSaleMapper.updateById(entity);
    }
  }
}

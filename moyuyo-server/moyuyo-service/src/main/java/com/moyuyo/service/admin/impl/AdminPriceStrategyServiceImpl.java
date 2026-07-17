package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.PriceStrategyEntity;
import com.moyuyo.dao.admin.mapper.PriceStrategyMapper;
import com.moyuyo.service.admin.AdminPriceStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台价格策略服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminPriceStrategyServiceImpl implements AdminPriceStrategyService {

  private final PriceStrategyMapper priceStrategyMapper;

  @Override
  public Page<Map<String, Object>> listAll(String type, Boolean enabled, int page, int size) {
    LambdaQueryWrapper<PriceStrategyEntity> wrapper = new LambdaQueryWrapper<>();
    if (type != null && !type.isEmpty()) {
      wrapper.eq(PriceStrategyEntity::getStrategyType, type);
    }
    if (enabled != null) {
      wrapper.eq(PriceStrategyEntity::getEnabled, enabled);
    }
    wrapper.orderByDesc(PriceStrategyEntity::getCreateTime);

    Page<PriceStrategyEntity> entityPage = priceStrategyMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("strategyName", e.getStrategyName());
      item.put("strategyType", e.getStrategyType());
      item.put("applyTo", e.getApplyTo());
      item.put("applyValue", e.getApplyValue());
      item.put("discountType", e.getDiscountType());
      item.put("discountValue", e.getDiscountValue());
      item.put("minAmount", e.getMinAmount());
      item.put("maxDiscount", e.getMaxDiscount());
      item.put("startTime", e.getStartTime());
      item.put("endTime", e.getEndTime());
      item.put("priority", e.getPriority());
      item.put("enabled", e.getEnabled());
      item.put("createTime", e.getCreateTime());
      item.put("updateTime", e.getUpdateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public void create(PriceStrategyEntity entity) {
    priceStrategyMapper.insert(entity);
  }

  @Override
  public void update(PriceStrategyEntity entity) {
    priceStrategyMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    priceStrategyMapper.deleteById(id);
  }

  @Override
  public void toggle(Long id, Boolean enabled) {
    PriceStrategyEntity entity = priceStrategyMapper.selectById(id);
    if (entity != null) {
      entity.setEnabled(enabled);
      priceStrategyMapper.updateById(entity);
    }
  }
}

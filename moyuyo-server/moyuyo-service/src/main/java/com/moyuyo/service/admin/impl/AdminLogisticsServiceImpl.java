package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.admin.AdminLogisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台物流管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogisticsServiceImpl implements AdminLogisticsService {

  private final LogisticsMapper logisticsMapper;
  private final OrderMapper orderMapper;

  @Override
  public Page<LogisticsEntity> listAll(String status, String carrier, int page, int size) {
    LambdaQueryWrapper<LogisticsEntity> wrapper = new LambdaQueryWrapper<>();
    if (carrier != null && !carrier.isEmpty()) {
      wrapper.eq(LogisticsEntity::getCarrier, carrier);
    }
    wrapper.orderByDesc(LogisticsEntity::getShippedAt);
    return logisticsMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public Map<String, Object> stats() {
    // 物流统计
    Long total = logisticsMapper.selectCount(new LambdaQueryWrapper<>());
    Long shipped = logisticsMapper.selectCount(
        new LambdaQueryWrapper<LogisticsEntity>().isNotNull(LogisticsEntity::getShippedAt));
    Long received = logisticsMapper.selectCount(
        new LambdaQueryWrapper<LogisticsEntity>().isNotNull(LogisticsEntity::getReceivedAt));

    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("shipped", shipped);
    result.put("received", received);
    result.put("inTransit", total - received);
    return result;
  }

  @Override
  public void updateCarrier(Long id, String carrier, String trackingNo) {
    LogisticsEntity entity = logisticsMapper.selectById(id);
    if (entity != null) {
      entity.setCarrier(carrier);
      entity.setTrackingNumber(trackingNo);
      logisticsMapper.updateById(entity);
    }
  }
}

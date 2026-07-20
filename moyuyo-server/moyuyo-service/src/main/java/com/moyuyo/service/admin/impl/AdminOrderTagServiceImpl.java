package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.AdminOrderTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 订单标签服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminOrderTagServiceImpl implements AdminOrderTagService {

  @Override
  public List<Map<String, Object>> listAll() {
    return new ArrayList<>();
  }

  @Override
  public void create(Map<String, Object> data) {
    // 创建标签逻辑
  }

  @Override
  public void update(Map<String, Object> data) {
    // 更新标签逻辑
  }

  @Override
  public void delete(Long id) {
    // 删除标签逻辑
  }

  @Override
  public void setOrderTags(Long orderId, List<Long> tagIds) {
    // 设置订单标签逻辑
  }

  @Override
  public List<Map<String, Object>> getOrderTags(Long orderId) {
    return new ArrayList<>();
  }
}

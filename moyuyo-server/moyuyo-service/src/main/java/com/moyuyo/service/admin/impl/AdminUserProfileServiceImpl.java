package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminUserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 管理后台用户画像服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserProfileServiceImpl implements AdminUserProfileService {

  private final UserMapper userMapper;
  private final OrderMapper orderMapper;

  @Override
  public Page<UserEntity> listAll(String keyword, Integer status, int page, int size) {
    LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
    if (keyword != null && !keyword.isEmpty()) {
      wrapper.like(UserEntity::getNickname, keyword)
          .or().like(UserEntity::getEmail, keyword)
          .or().like(UserEntity::getPhone, keyword);
    }
    if (status != null) {
      wrapper.eq(UserEntity::getStatus, status);
    }
    wrapper.orderByDesc(UserEntity::getCreatedAt);
    return userMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public Map<String, Object> stats() {
    // 用户统计
    Long total = userMapper.selectCount(new LambdaQueryWrapper<>());
    Long active = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getStatus, 1));

    Map<String, Object> result = new HashMap<>();
    result.put("total", total);
    result.put("active", active);
    result.put("inactive", total - active);
    return result;
  }

  @Override
  public Map<String, Object> getDetail(Long id) {
    // 查询用户基本信息
    UserEntity user = userMapper.selectById(id);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }

    // 查询用户的订单数
    Long orderCount = orderMapper.selectCount(
        new LambdaQueryWrapper<OrderEntity>().eq(OrderEntity::getUserId, id));
    // Java 端聚合已完成订单的总金额
    java.util.List<OrderEntity> completedOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, id)
            .eq(OrderEntity::getStatus, "COMPLETED"));
    java.math.BigDecimal totalConsumption = completedOrders.stream()
        .map(OrderEntity::getPayAmount)
        .filter(Objects::nonNull)
        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("userId", user.getId());
    result.put("nickname", user.getNickname());
    result.put("email", user.getEmail());
    result.put("phone", user.getPhone());
    result.put("status", user.getStatus());
    result.put("registerTime", user.getCreatedAt());
    result.put("orderCount", orderCount);
    result.put("totalConsumption", totalConsumption);
    return result;
  }

  @Override
  public void updateStatus(Long id, Integer status) {
    UserEntity entity = userMapper.selectById(id);
    if (entity != null) {
      entity.setStatus(status);
      userMapper.updateById(entity);
    }
  }

  @Override
  public void delete(Long id) {
    // 逻辑删除（通过 MyBatis Plus 的 @TableLogic 自动处理）
    userMapper.deleteById(id);
  }
}

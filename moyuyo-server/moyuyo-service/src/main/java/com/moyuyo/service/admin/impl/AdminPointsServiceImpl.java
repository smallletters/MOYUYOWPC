package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.service.admin.AdminPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 积分服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminPointsServiceImpl implements AdminPointsService {

  private final PointsLogMapper pointsLogMapper;

  @Override
  public List<Map<String, Object>> listActivities() {
    return new ArrayList<>();
  }

  @Override
  public void createActivity(Map<String, Object> data) {
    // 积分活动创建逻辑
  }

  @Override
  public Map<String, Object> listLogs(int page, int size, Long userId) {
    LambdaQueryWrapper<PointsLogEntity> wrapper = new LambdaQueryWrapper<PointsLogEntity>()
        .orderByDesc(PointsLogEntity::getCreatedAt);
    if (userId != null) {
      wrapper.eq(PointsLogEntity::getUserId, userId);
    }
    Page<PointsLogEntity> pageResult = pointsLogMapper.selectPage(new Page<>(page, size), wrapper);

    List<Map<String, Object>> list = new ArrayList<>();
    for (PointsLogEntity log : pageResult.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", log.getId());
      item.put("userId", log.getUserId());
      item.put("points", log.getChangeValue());
      item.put("type", log.getType());
      item.put("remark", log.getRemark());
      item.put("createTime", log.getCreatedAt());
      list.add(item);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return result;
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    long totalLogs = pointsLogMapper.selectCount(new LambdaQueryWrapper<>());
    // 统计总积分发放和消耗
    Long totalEarned = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>().eq(PointsLogEntity::getType, "EARN"))
        .stream().mapToLong(PointsLogEntity::getChangeValue).sum();
    Long totalSpent = pointsLogMapper.selectList(
        new LambdaQueryWrapper<PointsLogEntity>().eq(PointsLogEntity::getType, "SPEND"))
        .stream().mapToLong(PointsLogEntity::getChangeValue).sum();
    stats.put("totalLogs", totalLogs);
    stats.put("totalEarned", totalEarned);
    stats.put("totalSpent", totalSpent);
    stats.put("balance", totalEarned - totalSpent);
    return stats;
  }
}

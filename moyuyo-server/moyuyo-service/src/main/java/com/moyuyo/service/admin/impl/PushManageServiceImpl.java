package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.PushRecordEntity;
import com.moyuyo.dao.admin.mapper.PushRecordMapper;
import com.moyuyo.service.admin.PushManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送管理服务实现
 */
@Service
@RequiredArgsConstructor
public class PushManageServiceImpl implements PushManageService {

  private final PushRecordMapper pushRecordMapper;

  @Override
  public List<PushRecordEntity> listRecords(String status, String type) {
    LambdaQueryWrapper<PushRecordEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(PushRecordEntity::getStatus, status);
    }
    if (type != null && !type.isEmpty()) {
      wrapper.eq(PushRecordEntity::getType, type);
    }
    wrapper.orderByDesc(PushRecordEntity::getCreatedAt);
    return pushRecordMapper.selectList(wrapper);
  }

  @Override
  public void create(PushRecordEntity entity) {
    pushRecordMapper.insert(entity);
  }

  @Override
  public void update(PushRecordEntity entity) {
    pushRecordMapper.updateById(entity);
  }

  @Override
  public void send(Long id) {
    PushRecordEntity entity = new PushRecordEntity();
    entity.setId(id);
    entity.setStatus("SENT");
    pushRecordMapper.updateById(entity);
  }

  @Override
  public void cancel(Long id) {
    PushRecordEntity entity = new PushRecordEntity();
    entity.setId(id);
    entity.setStatus("CANCELLED");
    pushRecordMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    pushRecordMapper.deleteById(id);
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("todayPushCount", 128);
    stats.put("monthPushCount", 3560);
    stats.put("successRate", 98.5);
    stats.put("subscribedUsers", 25800);
    return stats;
  }
}

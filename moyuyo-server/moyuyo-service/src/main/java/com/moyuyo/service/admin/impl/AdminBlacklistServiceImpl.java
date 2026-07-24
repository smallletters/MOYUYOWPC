package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.BlacklistEntity;
import com.moyuyo.dao.admin.mapper.BlacklistMapper;
import com.moyuyo.service.admin.AdminBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑名单服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminBlacklistServiceImpl implements AdminBlacklistService {

  private final BlacklistMapper blacklistMapper;

  @Override
  public Map<String, Object> listAll(String type, int page, int size) {
    LambdaQueryWrapper<BlacklistEntity> wrapper = new LambdaQueryWrapper<>();
    if (type != null && !type.isEmpty()) {
      wrapper.eq(BlacklistEntity::getType, type);
    }
    wrapper.orderByDesc(BlacklistEntity::getCreateTime);

    Page<BlacklistEntity> pageObj = blacklistMapper.selectPage(new Page<>(page, size), wrapper);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", pageObj.getRecords());
    result.put("total", pageObj.getTotal());
    result.put("page", pageObj.getCurrent());
    result.put("size", pageObj.getSize());
    return result;
  }

  @Override
  @Transactional
  public void create(Map<String, Object> data) {
    BlacklistEntity entity = new BlacklistEntity();
    if (data.get("type") != null) entity.setType((String) data.get("type"));
    if (data.get("value") != null) entity.setValue((String) data.get("value"));
    if (data.get("reason") != null) entity.setReason((String) data.get("reason"));
    if (data.get("operatorId") != null) entity.setOperatorId(Long.valueOf(data.get("operatorId").toString()));
    if (data.get("expireTime") != null) entity.setExpireTime(LocalDateTime.parse(data.get("expireTime").toString()));
    entity.setStatus("ENABLED");
    blacklistMapper.insert(entity);
  }

  @Override
  @Transactional
  public void batchCreate(List<Map<String, Object>> list) {
    for (Map<String, Object> data : list) {
      create(data);
    }
  }

  @Override
  @Transactional
  public void update(Long id, Map<String, Object> data) {
    BlacklistEntity entity = blacklistMapper.selectById(id);
    if (entity == null) return;
    if (data.get("type") != null) entity.setType((String) data.get("type"));
    if (data.get("value") != null) entity.setValue((String) data.get("value"));
    if (data.get("reason") != null) entity.setReason((String) data.get("reason"));
    if (data.get("status") != null) entity.setStatus((String) data.get("status"));
    if (data.get("expireTime") != null) entity.setExpireTime(LocalDateTime.parse(data.get("expireTime").toString()));
    blacklistMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void delete(Long id) {
    blacklistMapper.deleteById(id);
  }
}

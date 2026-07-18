package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import com.moyuyo.dao.admin.mapper.SensitiveWordMapper;
import com.moyuyo.service.admin.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 敏感词管理服务实现
 */
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

  private final SensitiveWordMapper sensitiveWordMapper;

  @Override
  public List<SensitiveWordEntity> listAll(String category, String keyword) {
    LambdaQueryWrapper<SensitiveWordEntity> wrapper = new LambdaQueryWrapper<>();
    if (category != null && !category.isEmpty()) {
      wrapper.eq(SensitiveWordEntity::getCategory, category);
    }
    if (keyword != null && !keyword.isEmpty()) {
      wrapper.like(SensitiveWordEntity::getWord, keyword);
    }
    return sensitiveWordMapper.selectList(wrapper);
  }

  @Override
  public List<Map<String, Object>> categoryStats() {
    // 从数据库查询所有记录，按 category 分组统计
    List<SensitiveWordEntity> all = sensitiveWordMapper.selectList(new LambdaQueryWrapper<>());
    Map<String, Long> groupMap = all.stream()
        .collect(Collectors.groupingBy(SensitiveWordEntity::getCategory, Collectors.counting()));

    List<Map<String, Object>> result = groupMap.entrySet().stream().map(entry -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("code", entry.getKey());
      item.put("count", entry.getValue());
      return item;
    }).collect(Collectors.toList());
    return result;
  }

  @Override
  public void create(SensitiveWordEntity entity) {
    sensitiveWordMapper.insert(entity);
  }

  @Override
  public void update(SensitiveWordEntity entity) {
    sensitiveWordMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    sensitiveWordMapper.deleteById(id);
  }

  @Override
  public void batchDelete(List<Long> ids) {
    sensitiveWordMapper.deleteBatchIds(ids);
  }
}

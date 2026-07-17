package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import com.moyuyo.dao.admin.mapper.SensitiveWordMapper;
import com.moyuyo.service.admin.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.KnowledgeBaseEntity;
import com.moyuyo.dao.admin.mapper.KnowledgeBaseMapper;
import com.moyuyo.service.admin.AdminKnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台知识库服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminKnowledgeBaseServiceImpl implements AdminKnowledgeBaseService {

  private final KnowledgeBaseMapper knowledgeBaseMapper;

  @Override
  public Page<Map<String, Object>> listAll(String category, String keyword, int page, int size) {
    LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
    if (category != null && !category.isEmpty()) {
      wrapper.eq(KnowledgeBaseEntity::getCategory, category);
    }
    if (keyword != null && !keyword.isEmpty()) {
      wrapper.like(KnowledgeBaseEntity::getTitle, keyword);
    }
    wrapper.orderByDesc(KnowledgeBaseEntity::getCreateTime);

    Page<KnowledgeBaseEntity> entityPage = knowledgeBaseMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("category", e.getCategory());
      item.put("title", e.getTitle());
      item.put("content", e.getContent());
      item.put("tags", e.getTags());
      item.put("sortOrder", e.getSortOrder());
      item.put("viewCount", e.getViewCount());
      item.put("status", e.getStatus());
      item.put("createTime", e.getCreateTime());
      item.put("updateTime", e.getUpdateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public void create(KnowledgeBaseEntity entity) {
    knowledgeBaseMapper.insert(entity);
  }

  @Override
  public void update(KnowledgeBaseEntity entity) {
    knowledgeBaseMapper.updateById(entity);
  }

  @Override
  public void delete(Long id) {
    knowledgeBaseMapper.deleteById(id);
  }

  @Override
  public KnowledgeBaseEntity getById(Long id) {
    return knowledgeBaseMapper.selectById(id);
  }
}

package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.KnowledgeBaseEntity;

import java.util.Map;

/**
 * 管理后台知识库服务
 */
public interface AdminKnowledgeBaseService {

  /**
   * 知识库列表（分页、筛选）
   */
  Page<Map<String, Object>> listAll(String category, String keyword, int page, int size);

  /**
   * 创建文章
   */
  void create(KnowledgeBaseEntity entity);

  /**
   * 更新文章
   */
  void update(KnowledgeBaseEntity entity);

  /**
   * 删除文章
   */
  void delete(Long id);

  /**
   * 根据ID获取文章
   */
  KnowledgeBaseEntity getById(Long id);
}

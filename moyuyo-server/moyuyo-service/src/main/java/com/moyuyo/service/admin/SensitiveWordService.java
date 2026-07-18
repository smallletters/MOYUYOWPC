package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.SensitiveWordEntity;

import java.util.List;
import java.util.Map;

/**
 * 敏感词管理服务
 */
public interface SensitiveWordService {

  /**
   * 敏感词列表（支持筛选）
   */
  List<SensitiveWordEntity> listAll(String category, String keyword);

  /**
   * 分类统计（按 category 分组统计数量）
   */
  List<Map<String, Object>> categoryStats();

  /**
   * 创建敏感词
   */
  void create(SensitiveWordEntity entity);

  /**
   * 更新敏感词
   */
  void update(SensitiveWordEntity entity);

  /**
   * 删除敏感词
   */
  void delete(Long id);

  /**
   * 批量删除敏感词
   */
  void batchDelete(List<Long> ids);
}

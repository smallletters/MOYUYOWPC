package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台黑名单服务
 */
public interface AdminBlacklistService {

  /**
   * 黑名单列表（分页）
   */
  Map<String, Object> listAll(String type, int page, int size);

  /**
   * 添加黑名单
   */
  void create(Map<String, Object> data);

  /**
   * 批量添加黑名单
   */
  void batchCreate(List<Map<String, Object>> list);

  /**
   * 移除黑名单
   */
  void delete(Long id);

  /**
   * 更新黑名单
   */
  void update(Long id, Map<String, Object> data);
}

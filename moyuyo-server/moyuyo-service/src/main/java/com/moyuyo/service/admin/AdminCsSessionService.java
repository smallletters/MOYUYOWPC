package com.moyuyo.service.admin;

import java.util.Map;

/**
 * 管理后台客服会话服务
 */
public interface AdminCsSessionService {

  /**
   * 会话列表（分页）
   */
  Map<String, Object> listAll(int page, int size, String status);

  /**
   * 会话详情
   */
  Map<String, Object> getById(Long id);

  /**
   * 客服会话统计
   */
  Map<String, Object> getStats();
}

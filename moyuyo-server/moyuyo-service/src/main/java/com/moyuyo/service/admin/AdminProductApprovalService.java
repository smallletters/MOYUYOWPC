package com.moyuyo.service.admin;

import java.util.Map;

/**
 * 管理后台商品审核服务
 */
public interface AdminProductApprovalService {

  /**
   * 审核列表（分页、状态筛选）
   */
  Map<String, Object> listAll(int page, int size, String status);

  /**
   * 审核详情
   */
  Map<String, Object> getById(Long id);

  /**
   * 审核通过
   */
  void approve(Long id, Long reviewerId);

  /**
   * 审核驳回
   */
  void reject(Long id, Long reviewerId, String reason);

  /**
   * 标记加急
   */
  void setUrgent(Long id);
}

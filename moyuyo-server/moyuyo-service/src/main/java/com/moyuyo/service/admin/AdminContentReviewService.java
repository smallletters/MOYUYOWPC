package com.moyuyo.service.admin;

import java.util.Map;

/**
 * 管理后台内容审核服务
 */
public interface AdminContentReviewService {

  /**
   * 内容审核列表（分页）
   */
  Map<String, Object> listAll(int page, int size, String contentType, String status);

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
  void reject(Long id, Long reviewerId, String reason, String comment);
}

package com.moyuyo.service.admin;

import java.util.Map;

/**
 * 管理后台库存调拨服务
 */
public interface AdminInventoryTransferService {

  /**
   * 调拨单列表（分页）
   */
  Map<String, Object> listAll(int page, int size, String status);

  /**
   * 创建调拨单
   */
  void create(Map<String, Object> data);

  /**
   * 审批通过
   */
  void approve(Long id);

  /**
   * 审批驳回
   */
  void reject(Long id, String reason);

  /**
   * 完成调拨
   */
  void complete(Long id);
}

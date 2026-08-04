package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferCreateRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferVO;

/**
 * 管理后台库存调拨服务
 */
public interface AdminInventoryTransferService {

  /**
   * 调拨单列表(分页)
   *
   * @param page   页码(从 1 开始)
   * @param size   每页条数
   * @param status 前端状态值:pending/approved/completed/rejected,为空时查全部
   */
  PageResponse<InventoryTransferVO> listAll(int page, int size, String status);

  /**
   * 创建调拨单
   */
  void create(InventoryTransferCreateRequest request);

  /**
   * 审批通过
   */
  void approve(Long id);

  /**
   * 审批驳回
   *
   * @param id     调拨单ID
   * @param reason 驳回原因(可为空)
   */
  void reject(Long id, String reason);

  /**
   * 完成调拨
   */
  void complete(Long id);
}

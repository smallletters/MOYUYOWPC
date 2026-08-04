package com.moyuyo.common.dto.admin.inventory;

import lombok.Data;

/**
 * 管理后台库存调拨驳回请求
 */
@Data
public class InventoryTransferRejectRequest {

  /** 驳回原因 */
  private String reason;
}

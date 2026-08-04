package com.moyuyo.common.dto.admin.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台库存调拨创建请求
 */
@Data
public class InventoryTransferCreateRequest {

  /** SKU ID(必填) */
  @NotNull(message = "skuId 不能为空")
  private Long skuId;

  /** 调出仓库ID */
  private Long fromWarehouseId;

  /** 调出仓库名称(无 ID 时按名称查找) */
  private String fromWarehouse;

  /** 调入仓库ID */
  private Long toWarehouseId;

  /** 调入仓库名称(无 ID 时按名称查找) */
  private String toWarehouse;

  /** 调拨数量(必填,>=1) */
  @NotNull(message = "quantity 不能为空")
  @Min(value = 1, message = "quantity 必须 >= 1")
  private Integer quantity;

  /** 操作人ID */
  private Long operatorId;

  /** 调拨原因 */
  private String reason;
}

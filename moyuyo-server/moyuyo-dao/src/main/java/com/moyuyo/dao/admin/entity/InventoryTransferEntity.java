package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存调拨实体（对应 mo_inventory_transfer 表）
 */
@Data
@TableName("mo_inventory_transfer")
public class InventoryTransferEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** SKU ID */
  private Long skuId;

  /** 调出仓库ID */
  private Long fromWarehouseId;

  /** 调入仓库ID */
  private Long toWarehouseId;

  /** 调拨数量 */
  private Integer quantity;

  /** 调拨状态：PENDING/IN_TRANSIT/COMPLETED/REJECTED */
  private String status;

  /** 操作人ID */
  private Long operatorId;

  /** 审批人ID */
  private Long approverId;

  /** 调拨原因 */
  private String reason;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  /** 完成时间 */
  private LocalDateTime completeTime;
}

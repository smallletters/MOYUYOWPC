package com.moyuyo.common.dto.admin.inventory;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台库存调拨视图对象(列表/详情返回)
 */
@Data
public class InventoryTransferVO {

  private Long id;

  /** SKU ID */
  private Long skuId;

  /** 前端兼容字段,值同 skuId */
  private Long productId;

  /** 调出仓库ID */
  private Long fromWarehouseId;

  /** 调入仓库ID */
  private Long toWarehouseId;

  /** 调出仓库名称 */
  private String fromWarehouse;

  /** 调入仓库名称 */
  private String toWarehouse;

  /** 商品名称(暂以 SKU-{skuId} 占位) */
  private String productName;

  /** 调拨数量 */
  private Integer quantity;

  /** 调拨原因 */
  private String reason;

  /** 前端展示状态:pending/approved/completed/rejected */
  private String status;

  /** 数据库存储状态:PENDING/IN_TRANSIT/COMPLETED/REJECTED */
  private String dbStatus;

  /** 操作人ID */
  private Long operatorId;

  /** 审批人ID */
  private Long approverId;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createTime;

  /** 前端兼容字段,与 createTime 同值 */
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createdAt;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime updateTime;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime completeTime;
}

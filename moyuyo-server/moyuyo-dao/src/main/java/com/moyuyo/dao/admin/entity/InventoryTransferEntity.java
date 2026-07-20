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

    /** 调拨单号 */
    private String transferNo;

    /** 调出仓库ID */
    private Long fromWarehouseId;

    /** 调入仓库ID */
    private Long toWarehouseId;

    /** 商品ID */
    private Long productId;

    /** SKU ID */
    private Long skuId;

    /** 调拨数量 */
    private Integer quantity;

    /** 调拨状态：PENDING/PROCESSING/COMPLETED/CANCELLED */
    private String status;

    /** 操作人ID */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

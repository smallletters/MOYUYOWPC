package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库库存关系（对应 mo_inventory 表）
 * 一个 SKU 在某个仓库里有一条记录，记录当前在库量 / 锁定量 / 安全水位。
 * 设计动机：原 mo_product.stock 只记录全平台汇总库存，无法支撑"按仓库维度"展示库存分布。
 */
@Data
@TableName("mo_inventory")
public class InventoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仓库 ID（→mo_warehouse.id） */
    private Long warehouseId;

    /** 商品 ID（→mo_product.id） */
    private Long productId;

    /** SKU ID（→mo_product_sku.id，可空：仅记录 SPU 级） */
    private Long skuId;

    /** 当前在库数量 */
    private Integer quantity;

    /** 锁定中数量（已下单未发货） */
    private Integer lockedQuantity;

    /** 安全库存下限 */
    private Integer safetyStock;

    /** 最近入库时间 */
    private LocalDateTime lastInTime;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
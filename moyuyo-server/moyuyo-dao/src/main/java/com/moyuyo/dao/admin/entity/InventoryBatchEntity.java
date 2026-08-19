package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存批次实体（对应 mo_inventory_batch 表）
 * 字段语义：
 *  - batch_no：批次编号，唯一
 *  - sku_id：关联 mo_product_sku.id
 *  - strategy：FIFO（先入先出）/ FEFO（先到期先出）
 *  - status：NORMAL(在库) / EXPIRING(<=30天临期) / EXPIRED(已过期)
 */
@Data
@TableName("mo_inventory_batch")
public class InventoryBatchEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 批次编号 */
    private String batchNo;

    /** SKU ID（关联 mo_product_sku.id） */
    private Long skuId;

    /** 商品名称（冗余字段，便于列表展示） */
    private String productName;

    /** 入库日期 */
    private LocalDateTime inDate;

    /** 有效期至（非食品可为空） */
    private LocalDateTime expireDate;

    /** 批次库存数量 */
    private Integer quantity;

    /** 出库策略：FIFO / FEFO */
    private String strategy;

    /** 状态：NORMAL / EXPIRING / EXPIRED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

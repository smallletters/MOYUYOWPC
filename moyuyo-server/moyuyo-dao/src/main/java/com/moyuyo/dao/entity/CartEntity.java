package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_cart")
public class CartEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 商品ID（冗余自 SKU，购物车列表展示 / 结算联表定位商品使用） */
    private Long productId;

    private Long skuId;

    /** 商品名称快照：加购时写入，避免商品改名后历史购物车展示漂移 */
    private String productName;

    /** 商品主图快照：加购时写入 */
    private String mainImage;

    /** 加购时单价快照 */
    private BigDecimal price;

    private Integer quantity;

    /** 是否勾选结算（对应表 selected 列） */
    private Boolean selected;

    /**
     * 当前可用库存（非表字段，查询购物车时联查填充）
     * 仅用于购物车页前端限制加购数量上限，不落库
     */
    @TableField(exist = false)
    private Integer stock;

    /**
     * 购物车条目可用状态（非表字段，查询时联查计算），取值：
     * VALID=可售 / OUT_OF_STOCK=缺货 / OFF_SALE=已下架 /
     * PRODUCT_GONE=商品已删除 / INVALID_SKU=规格(SKU)已失效
     * 供购物车页按电商惯例分区展示与禁用结算
     */
    @TableField(exist = false)
    private String cartStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

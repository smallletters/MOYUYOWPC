package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("mo_product")
public class ProductEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String spuCode;

    private Long wooProductId;

    private Long categoryId;

    private Long brandId;

    private Long brandIpId;

    private String name;

    private String mainImage;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private String stockStatus;

    private Integer sales;

    private String attributes;

    private String detail;

    private String shortDetail;

    private java.math.BigDecimal weight;

    private String tags;

    private String productType;

    private Boolean manageStock;

    private Boolean onSale;

    private LocalDateTime wooModified;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<ProductSkuEntity> skus;

    @TableField(exist = false)
    private List<ProductImageEntity> images;

    /** 平均评分（仅 Controller 层动态注入，无对应数据库列） */
    @TableField(exist = false)
    private Double rating;

    /** 有效评论数（仅 Controller 层动态注入） */
    @TableField(exist = false)
    private Integer reviewCount;

    /** 会员价（仅 Controller 层按当前用户会员等级动态注入，无对应数据库列） */
    @TableField(exist = false)
    private BigDecimal memberPrice;

    /** 会员折扣系数（1.00 = 原价，0.95 = 9.5 折；前端按当前 SKU 价 × 系数自行计算展示价） */
    @TableField(exist = false)
    private BigDecimal memberPriceRate;

    /** 是否处于进行中的秒杀活动（秒杀商品不展示/不叠加会员价） */
    @TableField(exist = false)
    private Boolean flashSaleActive;
}

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
}

package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_flash_sale")
public class FlashSaleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private Long productId;

    private Long skuId;

    private BigDecimal flashPrice;

    private BigDecimal originalPrice;

    private Integer totalStock;

    private Integer soldStock;

    private Integer limitPerUser;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Boolean active;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 关联商品主图（仅列表/详情接口动态注入，无对应数据库列） */
    @TableField(exist = false)
    private String productImage;

    /** 关联商品名称（仅列表/详情接口动态注入，无对应数据库列） */
    @TableField(exist = false)
    private String productName;
}

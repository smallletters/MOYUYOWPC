package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_order_item")
public class OrderItemEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long orderId;

  private Long productId;

  private Long skuId;

  private String productName;

  private String skuSpec;

  private String mainImage;

  private BigDecimal price;

  private Integer quantity;

  private BigDecimal subtotal;

  /**
   * 冗余字段：SKU 编码（来自 mo_product_sku.sku_code）
   * - 变体商品：对应 skuId 行的 sku_code
   * - 简单商品：对应 productId 行的单条默认 SKU 的 sku_code
   * 管理后台订单详情页展示用，避免前端 JOIN 不到
   */
  @TableField(exist = false)
  private String skuCode;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

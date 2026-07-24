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

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

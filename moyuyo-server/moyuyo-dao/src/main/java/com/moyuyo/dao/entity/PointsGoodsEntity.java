package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("mo_points_goods")
public class PointsGoodsEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String name;

  private String description;

  private String image;

  /** 分类：DIGITAL / COUPON / COUPON_FREESHIP / PHYSICAL */
  private String category;

  /** 兑换所需积分 */
  private Integer points;

  /** 库存，-1 表示不限 */
  private Integer stock;

  /** 累计已兑换数量 */
  private Integer totalExchanged;

  /** 实物是否需要收货地址 */
  private Boolean needAddress;

  /** 0 下架 1 上架 */
  private Integer status;

  private Integer sortOrder;

  /** 非表字段：可兑换的 SKU 列表，APP 兑换变体商品时选择规格使用 */
  @TableField(exist = false)
  private List<ProductSkuEntity> skus;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
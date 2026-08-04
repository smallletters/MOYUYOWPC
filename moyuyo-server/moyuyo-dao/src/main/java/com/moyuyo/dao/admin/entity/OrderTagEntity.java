package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单标签实体（对应 mo_order_tag 表）
 */
@Data
@TableName("mo_order_tag")
public class OrderTagEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 标签名称 */
  private String name;

  /** 标签颜色 */
  private String color;

  /** 标签描述 */
  private String description;

  /** 排序号 */
  private Integer sortOrder;

  /** 是否启用：1启用 0禁用 */
  private Integer enabled;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}

package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_help_category")
public class HelpCategoryEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String name;

  private String icon;

  private Integer sortOrder;

  private Integer active;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
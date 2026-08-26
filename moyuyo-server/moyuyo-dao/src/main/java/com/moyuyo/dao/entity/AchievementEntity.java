package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_achievement")
public class AchievementEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private String code;

  private String name;

  private String description;

  private String icon;

  private String badgeImage;

  private Integer pointsReward;

  private String conditionExpr;

  /** COMMON / RARE / EPIC / LEGEND */
  private String category;

  private Integer active;

  private Integer sortOrder;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
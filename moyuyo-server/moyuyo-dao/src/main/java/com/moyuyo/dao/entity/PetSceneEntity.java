package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_pet_scene")
public class PetSceneEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long petId;

  private String sceneCode;

  private Boolean unlocked;

  private LocalDateTime unlockedAt;

  private Integer sortOrder;

  // mo_pet_scene 表无时间列，这两个字段仅为代码兼容保留，不参与 SQL
  @TableField(exist = false)
  private LocalDateTime createdAt;

  @TableField(exist = false)
  private LocalDateTime updatedAt;
}

package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_pet_achievement")
public class PetAchievementEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long petId;

  private String achievementCode;

  private Boolean unlocked;

  private LocalDateTime unlockedAt;

  private Integer progress;

  // 表列名为 create_time/update_time（非 created_at/updated_at），显式映射避免 SQL 报错
  @TableField(value = "create_time", fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
}

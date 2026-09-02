package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mo_user_mission")
public class UserMissionEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private Long missionId;

  private Integer progress;

  private Integer completed;

  private Integer claimed;

  // 任务进度所属周期:DAILY=今天,WEEKLY=本周一;过期时 Service 层自动重置 progress=0
  private LocalDate cycleDate;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

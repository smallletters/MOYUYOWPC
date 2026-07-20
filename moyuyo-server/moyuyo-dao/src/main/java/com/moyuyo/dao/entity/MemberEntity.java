package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_member")
public class MemberEntity {

  public enum Level {
    NORMAL, SILVER, GOLD, DIAMOND
  }

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private Level level;

  private Integer growthValue;

  private LocalDateTime levelExpireAt;

  @TableField(exist = false)
  private Integer deleted;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

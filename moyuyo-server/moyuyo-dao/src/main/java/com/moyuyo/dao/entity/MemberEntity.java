package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_member")
public class MemberEntity {

  public enum Level {
    NORMAL, SILVER, GOLD, DIAMOND, PLATINUM
  }

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  /** 会员卡号：随机唯一 12 位数字（不含 MY. 前缀），首次访问会员中心时生成 */
  private String memberNo;

  private Level level;

  private Integer growthValue;

  private LocalDateTime levelExpireAt;

  @TableField(exist = false)
  private Integer deleted;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

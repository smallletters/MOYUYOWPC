package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 月度邀请排行奖励发放记录：(month, user_id) 唯一，保证同一月份不会重复发放。
 */
@Data
@TableName("mo_invite_month_bonus")
public class InviteMonthlyBonusEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 归属月份，格式 yyyy-MM（美国东部时间） */
  private String month;

  private Long userId;

  private Integer rankNo;

  private Integer points;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

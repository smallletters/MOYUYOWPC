package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_invite")
public class InviteEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String inviteCode;

  private Long invitedUserId;

  private String status;

  private Integer pointsAwarded;

  /** 触发邀请奖励的订单 ID（退款时用于精确扣回该笔奖励） */
  private Long triggerOrderId;

  /** 邀请奖励发放时间（用于月度计奖上限统计） */
  private LocalDateTime rewardTime;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

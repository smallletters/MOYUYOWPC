package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户固定邀请码：每个用户仅一条（user_id 唯一），邀请码长期不变。
 */
@Data
@TableName("mo_invite_code")
public class InviteCodeEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String inviteCode;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

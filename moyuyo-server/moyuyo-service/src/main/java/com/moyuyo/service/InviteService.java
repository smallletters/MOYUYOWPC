package com.moyuyo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.dao.entity.InviteEntity;

import java.util.Map;

public interface InviteService {

  String getInviteCode(Long userId);

  Map<String, Object> getInviteStats(Long userId);

  IPage<InviteEntity> getInviteHistory(Long userId, int page, int size);

  /**
   * 绑定被邀请人：当新用户使用邀请码注册时调用。
   * 将 invite.status 从 PENDING → REGISTERED；若邀请人完成首单则发放双方奖励（调用方触发）。
   */
  InviteEntity bindInvitee(String inviteCode, Long inviteeUserId);

  /**
   * 标记邀请完成（被邀请人首单完成后调用），双方各得 200 积分。
   */
  InviteEntity markOrdered(String inviteCode, Long inviteeUserId);
}

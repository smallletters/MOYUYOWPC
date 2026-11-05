package com.moyuyo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.dao.entity.InviteEntity;

import java.util.Map;

public interface InviteService {

  String getInviteCode(Long userId);

  Map<String, Object> getInviteStats(Long userId);

  IPage<InviteEntity> getInviteHistory(Long userId, int page, int size);

  /**
   * 本月邀请排行：返回当前用户的名次（0 = 本月未邀请成功）、本月邀请人数与所属月份。
   * 月份按美国东部时间划分。
   */
  Map<String, Object> getMonthlyRanking(Long userId);

  /**
   * 绑定被邀请人：当新用户使用邀请码注册时调用。
   * 同一邀请码可被多位好友复用；每个被邀请人只能绑定一次，不能绑定自己的邀请码。
   */
  InviteEntity bindInvitee(String inviteCode, Long inviteeUserId);

  /**
   * 被邀请人首单支付成功后调用，双方各得 200 积分；重复调用幂等。
   * 单月计奖超过 20 条时仅标记完成、不再发放积分。
   * 由订单服务内部触发，不对外暴露。
   */
  InviteEntity markOrderedByInvitee(Long inviteeUserId, Long orderId);

  /**
   * 退款完成时扣回由该订单触发的邀请奖励，并把邀请关系恢复为可再次触发。
   * 由退款服务内部触发，不对外暴露。
   */
  InviteEntity revokeInviteRewardOnRefund(Long orderId, String orderNo, Long inviteeUserId);

  /**
   * 发放上一个自然月（美国东部时间）邀请排行 TOP10 阶梯积分奖励。
   * 返回实际发放人数；同一月份重复调用不会重复发放。
   */
  int grantMonthlyRankBonus();
}

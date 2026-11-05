package com.moyuyo.service.schedule;

import com.moyuyo.service.InviteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 月度邀请排行奖励任务：
 * 每月 1 号 00:10（美国东部时间）结算上一个自然月的 TOP10 阶梯积分奖励。
 * 幂等由 mo_invite_month_bonus 的 unique(month, user_id) 保证，重复执行/补跑不会重复发放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteMonthlyRankBonusJob {

  private final InviteService inviteService;

  @Value("${moyuyo.invite.monthly-bonus-enabled:true}")
  private boolean enabled;

  @Scheduled(
      cron = "${moyuyo.invite.monthly-bonus-cron:0 10 0 1 * ?}",
      zone = "America/New_York")
  public void grantMonthlyRankBonus() {
    if (!enabled) {
      return;
    }
    try {
      inviteService.grantMonthlyRankBonus();
    } catch (Exception e) {
      log.error("[invite] monthly rank bonus job failed", e);
    }
  }
}

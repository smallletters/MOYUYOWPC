package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.dto.InviteMonthlyRankRow;
import com.moyuyo.dao.entity.InviteCodeEntity;
import com.moyuyo.dao.entity.InviteEntity;
import com.moyuyo.dao.entity.InviteMonthlyBonusEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.InviteCodeMapper;
import com.moyuyo.dao.mapper.InviteMapper;
import com.moyuyo.dao.mapper.InviteMonthlyBonusMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.InviteService;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.config.MembershipRuleConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

  private final InviteMapper inviteMapper;
  private final InviteCodeMapper inviteCodeMapper;
  private final InviteMonthlyBonusMapper inviteMonthlyBonusMapper;
  private final UserMapper userMapper;
  private final PointsLogMapper pointsLogMapper;
  /** 退款扣回积分时复用统一积分服务（含会员等级重算） */
  private final MemberService memberService;
  // 任务中心埋点：邀请注册成功触发"邀请 1 位好友 / 累计邀请 10 位好友"
  private final MissionService missionService;
  /** 邀请奖励积分与月度计奖上限从会员规则配置读取（后台可调） */
  private final MembershipRuleConfigService ruleConfigService;

  /** 月度排行/月度计奖上限统一按美国东部时间划分月份 */
  private static final ZoneId RANKING_ZONE = ZoneId.of("America/New_York");

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public String getInviteCode(Long userId) {
    // 一人一码：邀请码独立存于 mo_invite_code（user_id 唯一），不随邀请次数变化
    InviteCodeEntity existing = inviteCodeMapper.selectOne(
        new LambdaQueryWrapper<InviteCodeEntity>()
            .eq(InviteCodeEntity::getUserId, userId));

    if (existing != null) {
      return existing.getInviteCode();
    }

    // 并发首次进入时复用对方生成的码；随机码撞库时换码重试
    for (int attempt = 0; attempt < 3; attempt++) {
      String code = generateInviteCode();
      InviteCodeEntity entity = new InviteCodeEntity();
      entity.setUserId(userId);
      entity.setInviteCode(code);
      try {
        inviteCodeMapper.insert(entity);
        return code;
      } catch (DuplicateKeyException e) {
        InviteCodeEntity raced = inviteCodeMapper.selectOne(
            new LambdaQueryWrapper<InviteCodeEntity>()
                .eq(InviteCodeEntity::getUserId, userId));
        if (raced != null) {
          return raced.getInviteCode();
        }
        // 未命中 userId，说明是邀请码本身撞库，换一个码继续
        log.warn("[invite] invite code collision, retry attempt={}", attempt + 1);
      }
    }
    throw new IllegalStateException("邀请码生成失败，请稍后重试");
  }

  @Override
  public Map<String, Object> getInviteStats(Long userId) {
    List<InviteEntity> records = inviteMapper.selectList(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getUserId, userId));

    long invitedCount = records.stream()
        .filter(r -> r.getInvitedUserId() != null)
        .count();

    int earnedPoints = records.stream()
        .mapToInt(r -> r.getPointsAwarded() != null ? r.getPointsAwarded() : 0)
        .sum();

    long completedOrders = records.stream()
        .filter(r -> "ORDERED".equals(r.getStatus()))
        .count();

    Map<String, Object> stats = new HashMap<>();
    stats.put("invitedCount", invitedCount);
    stats.put("earnedPoints", earnedPoints);
    stats.put("completedOrders", completedOrders);
    return stats;
  }

  @Override
  public IPage<InviteEntity> getInviteHistory(Long userId, int page, int size) {
    return inviteMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getUserId, userId)
            .isNotNull(InviteEntity::getInvitedUserId)
            .orderByDesc(InviteEntity::getCreateTime));
  }

  @Override
  public Map<String, Object> getMonthlyRanking(Long userId) {
    // 月度口径：美国东部时间本月 1 号 0 点起成功绑定的邀请关系数
    MonthWindow window = currentMonthWindow();
    Long monthlyCount = inviteMapper.selectCount(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getUserId, userId)
            .isNotNull(InviteEntity::getInvitedUserId)
            .ge(InviteEntity::getCreateTime, window.start())
            .lt(InviteEntity::getCreateTime, window.end()));
    long invitedCount = monthlyCount == null ? 0 : monthlyCount;

    // 并列同名次：名次 = 本月邀请数严格高于自己的人数 + 1；本月未邀请则为 0（未上榜）
    long rank = invitedCount > 0
        ? inviteMapper.countUsersAhead(window.start(), window.end(), invitedCount) + 1
        : 0;

    Map<String, Object> result = new HashMap<>();
    result.put("rank", rank);
    result.put("invitedCount", invitedCount);
    result.put("month", window.month());
    // 排行奖励金额：C 端邀请页展示与后台配置保持一致
    MembershipRuleConfigService.MembershipRuleConfig rule = ruleConfigService.getConfig();
    Map<String, Object> rankBonus = new HashMap<>();
    rankBonus.put("rank1", rule.rankBonus1());
    rankBonus.put("rank2", rule.rankBonus2());
    rankBonus.put("rank3", rule.rankBonus3());
    rankBonus.put("rank4To10", rule.rankBonus4To10());
    result.put("rankBonus", rankBonus);
    return result;
  }

  /**
   * 被邀请人通过邀请码注册时调用。
   * 同一邀请码可被多位好友复用：每次绑定插入一条独立的邀请关系记录，邀请码本身保持稳定。
   */
  @Override
  @Transactional
  public InviteEntity bindInvitee(String inviteCode, Long inviteeUserId) {
    if (inviteCode == null || inviteCode.isBlank() || inviteeUserId == null) {
      return null;
    }
    // 邀请码统一大写，避免用户手动输入小写导致匹配失败
    String code = inviteCode.trim().toUpperCase();

    // 邀请码在固定码表中全局唯一，直接定位邀请人
    InviteCodeEntity owner = inviteCodeMapper.selectOne(
        new LambdaQueryWrapper<InviteCodeEntity>()
            .eq(InviteCodeEntity::getInviteCode, code));
    if (owner == null || owner.getUserId() == null) {
      return null; // 邀请码不存在
    }
    // 禁止自己邀请自己，且每个用户只能被邀请一次，避免刷积分
    if (owner.getUserId().equals(inviteeUserId)) {
      return null;
    }
    Long boundCount = inviteMapper.selectCount(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getInvitedUserId, inviteeUserId));
    if (boundCount != null && boundCount > 0) {
      return null; // 该用户已绑定过邀请关系
    }

    // 邀请关系独立成行；邀请码继续保留给后续好友使用
    InviteEntity relation = new InviteEntity();
    relation.setUserId(owner.getUserId());
    relation.setInviteCode(code);
    relation.setInvitedUserId(inviteeUserId);
    relation.setStatus("REGISTERED");
    relation.setPointsAwarded(0);
    inviteMapper.insert(relation);

    log.info("Invite bind: inviteCode={} inviterUserId={} inviteeUserId={}",
        code, owner.getUserId(), inviteeUserId);

    // 任务中心埋点：邀请人累计邀请数和成就进度
    try {
      missionService.incrementByKeyword(owner.getUserId(), "WEEKLY", "邀请 1 位好友", 1);
      missionService.incrementByKeyword(owner.getUserId(), "ACHIEVEMENT", "邀请 10 位好友", 1);
    } catch (Exception e) {
      log.warn("[invite] trigger mission failed: inviteCode={}, reason={}", code, e.getMessage());
    }

    return relation;
  }

  /**
   * 被邀请人首单支付成功后发放双方奖励并标记 ORDERED；重复调用幂等。
   * 只根据 invitedUserId 反查关系，避免前端传入任意邀请码伪造奖励。
   * 独立事务：奖励失败只回滚自身，不会把支付回调主事务标记为 rollback-only。
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public InviteEntity markOrderedByInvitee(Long inviteeUserId, Long orderId) {
    if (inviteeUserId == null) {
      return null;
    }
    InviteEntity invite = inviteMapper.selectOne(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getInvitedUserId, inviteeUserId)
            .orderByAsc(InviteEntity::getId)
            .last("LIMIT 1"));
    if (invite == null) {
      return null; // 该用户不是被邀请用户
    }
    if ("ORDERED".equals(invite.getStatus())) {
      return invite; // 已发放过奖励，避免重复
    }

    // 邀请奖励金额与月度计奖上限：后台可配置，缺失时回退默认值
    MembershipRuleConfigService.MembershipRuleConfig rule = ruleConfigService.getConfig();
    int rewardPoints = rule.inviteRewardPoints();
    int monthlyRewardLimit = rule.inviteMonthlyLimit();

    LocalDateTime rewardTime = LocalDateTime.now();
    // 抢占式状态更新：并发支付回调时只有一次能更新成功，避免重复发积分
    int claimed = inviteMapper.update(null,
        new LambdaUpdateWrapper<InviteEntity>()
            .eq(InviteEntity::getId, invite.getId())
            .ne(InviteEntity::getStatus, "ORDERED")
            .set(InviteEntity::getStatus, "ORDERED")
            .set(InviteEntity::getTriggerOrderId, orderId)
            .set(InviteEntity::getRewardTime, rewardTime));
    if (claimed == 0) {
      return invite;
    }
    invite.setStatus("ORDERED");
    invite.setTriggerOrderId(orderId);
    invite.setRewardTime(rewardTime);

    // 月度计奖上限：只统计本月已实际发过积分的邀请（当前这条刚标记、尚未发分，不计入）
    MonthWindow window = currentMonthWindow();
    Long rewardedThisMonth = inviteMapper.selectCount(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getUserId, invite.getUserId())
            .eq(InviteEntity::getStatus, "ORDERED")
            .gt(InviteEntity::getPointsAwarded, 0)
            .ge(InviteEntity::getRewardTime, window.start())
            .lt(InviteEntity::getRewardTime, window.end()));
    if (rewardedThisMonth != null && rewardedThisMonth >= monthlyRewardLimit) {
      log.info("Invite reward skipped by monthly limit: inviterUserId={} limit={} inviteeUserId={}",
          invite.getUserId(), monthlyRewardLimit, inviteeUserId);
      invite.setPointsAwarded(0);
      return invite;
    }

    // 邀请人获得积分（兼容历史自邀脏数据：邀请人 == 被邀请人时只发一次）
    if (invite.getUserId() != null && !invite.getUserId().equals(inviteeUserId)) {
      awardPoints(invite.getUserId(), rewardPoints, "INVITE", String.valueOf(inviteeUserId),
          "邀请好友首单奖励");
    }

    // 被邀请人获得积分
    awardPoints(inviteeUserId, rewardPoints, "INVITE", String.valueOf(invite.getUserId()),
        "受邀首单奖励");

    inviteMapper.update(null,
        new LambdaUpdateWrapper<InviteEntity>()
            .eq(InviteEntity::getId, invite.getId())
            .set(InviteEntity::getPointsAwarded, rewardPoints));
    invite.setPointsAwarded(rewardPoints);

    log.info("Invite ordered: inviteId={} inviterUserId={} inviteeUserId={} orderId={} pointsEach={}",
        invite.getId(), invite.getUserId(), inviteeUserId, orderId, rewardPoints);
    return invite;
  }

  /**
   * 退款完成时扣回由该订单触发的邀请奖励，并把关系恢复为 REGISTERED（后续订单可再次触发）。
   * 若任一方可用积分不足，则保留 ORDERED 并告警，留待人工对账。
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public InviteEntity revokeInviteRewardOnRefund(Long orderId, String orderNo, Long inviteeUserId) {
    if (orderId == null || inviteeUserId == null) {
      return null;
    }
    InviteEntity invite = inviteMapper.selectOne(
        new LambdaQueryWrapper<InviteEntity>()
            .eq(InviteEntity::getTriggerOrderId, orderId)
            .eq(InviteEntity::getInvitedUserId, inviteeUserId)
            .eq(InviteEntity::getStatus, "ORDERED")
            .last("LIMIT 1"));
    if (invite == null) {
      return null; // 该订单不是触发邀请奖励的订单
    }

    int awarded = invite.getPointsAwarded() == null ? 0 : invite.getPointsAwarded();
    if (awarded > 0) {
      boolean needInviter = invite.getUserId() != null
          && !invite.getUserId().equals(inviteeUserId);
      // 先同时校验双方可用余额，任一不足则整笔不扣，避免只扣一方造成账目不一致
      boolean enough = (!needInviter || hasAvailablePoints(invite.getUserId(), awarded))
          && hasAvailablePoints(inviteeUserId, awarded);
      if (!enough) {
        log.warn("[invite] refund clawback skipped (insufficient available points): "
            + "orderId={} inviteeUserId={}", orderId, inviteeUserId);
        return invite;
      }
      if (needInviter) {
        deductPoints(invite.getUserId(), awarded, orderNo, "退款扣回邀请奖励");
      }
      deductPoints(inviteeUserId, awarded, orderNo, "退款扣回受邀奖励");
    }

    inviteMapper.update(null,
        new LambdaUpdateWrapper<InviteEntity>()
            .eq(InviteEntity::getId, invite.getId())
            .set(InviteEntity::getStatus, "REGISTERED")
            .set(InviteEntity::getPointsAwarded, 0)
            .set(InviteEntity::getTriggerOrderId, null)
            .set(InviteEntity::getRewardTime, null));

    invite.setStatus("REGISTERED");
    invite.setPointsAwarded(0);
    invite.setTriggerOrderId(null);
    invite.setRewardTime(null);

    log.info("Invite reward revoked by refund: orderId={} inviteeUserId={} points={}",
        orderId, inviteeUserId, awarded);
    return invite;
  }

  /**
   * 发放上一个自然月邀请排行 TOP10 阶梯奖励，金额从会员规则配置读取。
   * mo_invite_month_bonus 唯一键保证重复执行不重复发放。
   */
  @Override
  @Transactional
  public int grantMonthlyRankBonus() {
    MonthWindow window = previousMonthWindow();
    List<InviteMonthlyRankRow> top =
        inviteMapper.selectMonthlyTopInviters(window.start(), window.end(), 10);
    MembershipRuleConfigService.MembershipRuleConfig rule = ruleConfigService.getConfig();

    int granted = 0;
    for (int i = 0; i < top.size(); i++) {
      InviteMonthlyRankRow row = top.get(i);
      if (row.getUserId() == null
          || row.getInviteCount() == null
          || row.getInviteCount() <= 0) {
        continue;
      }
      int bonusPoints = rankBonusPoints(i + 1, rule);
      if (bonusPoints <= 0) {
        continue; // 后台配置为 0 表示该名次不发奖励
      }

      InviteMonthlyBonusEntity record = new InviteMonthlyBonusEntity();
      record.setMonth(window.month());
      record.setUserId(row.getUserId());
      record.setRankNo(i + 1);
      record.setPoints(bonusPoints);
      try {
        inviteMonthlyBonusMapper.insert(record);
      } catch (DuplicateKeyException e) {
        continue; // 该月该用户已发放过，幂等跳过
      }

      awardPoints(row.getUserId(), bonusPoints, "INVITE", window.month(),
          "月度邀请排行第" + (i + 1) + "名奖励");
      granted++;
    }

    log.info("Monthly invite rank bonus finished: month={} granted={}", window.month(), granted);
    return granted;
  }

  private int rankBonusPoints(int rank, MembershipRuleConfigService.MembershipRuleConfig rule) {
    return switch (rank) {
      case 1 -> rule.rankBonus1();
      case 2 -> rule.rankBonus2();
      case 3 -> rule.rankBonus3();
      default -> rule.rankBonus4To10(); // 第 4-10 名
    };
  }

  private void awardPoints(Long userId, int changeValue, String type, String bizNo, String remark) {
    if (userId == null) return;
    UserEntity user = userMapper.selectById(userId);
    if (user == null) return;
    int newPoints = (user.getPoints() == null ? 0 : user.getPoints()) + changeValue;
    if (newPoints < 0) return;
    user.setPoints(newPoints);
    userMapper.updateById(user);

    PointsLogEntity logEntity = new PointsLogEntity();
    logEntity.setUserId(userId);
    logEntity.setChangeValue(changeValue);
    logEntity.setType(type);
    logEntity.setBizNo(bizNo);
    logEntity.setRemark(remark);
    pointsLogMapper.insert(logEntity);
  }

  /**
   * 校验可用余额（总积分 - 冻结积分）。
   */
  private boolean hasAvailablePoints(Long userId, int points) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
      return false;
    }
    int balance = user.getPoints() == null ? 0 : user.getPoints();
    int frozen = user.getPointsFrozen() == null ? 0 : user.getPointsFrozen();
    if (balance - frozen < points) {
      log.warn("[invite] available points insufficient: userId={} need={} balance={} frozen={}",
          userId, points, balance, frozen);
      return false;
    }
    return true;
  }

  /**
   * 扣回积分：调用前必须已通过 hasAvailablePoints 校验。
   * 通过 MemberService 扣减，保证会员等级同步重算。
   */
  private void deductPoints(Long userId, int points, String bizNo, String remark) {
    memberService.addPoints(userId, -points, "REFUND_CLAWBACK", bizNo, remark);
  }

  /** 当前自然月（美国东部时间）对应的服务器本地时间区间 */
  private MonthWindow currentMonthWindow() {
    return monthWindow(ZonedDateTime.now(RANKING_ZONE));
  }

  /** 上一个自然月（美国东部时间）对应的服务器本地时间区间 */
  private MonthWindow previousMonthWindow() {
    return monthWindow(ZonedDateTime.now(RANKING_ZONE).minusMonths(1));
  }

  private MonthWindow monthWindow(ZonedDateTime anchor) {
    ZonedDateTime start = anchor.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    ZonedDateTime end = start.plusMonths(1);
    // DB 中的 create_time 是服务器本地时间（LocalDateTime），需把时区月份边界换算成本地时间
    return new MonthWindow(
        YearMonth.from(start).toString(),
        LocalDateTime.ofInstant(start.toInstant(), ZoneId.systemDefault()),
        LocalDateTime.ofInstant(end.toInstant(), ZoneId.systemDefault()));
  }

  private String generateInviteCode() {
    return "MY" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }

  /** 月份窗口：month=yyyy-MM，start/end 为服务器本地时间（左闭右开） */
  private record MonthWindow(String month, LocalDateTime start, LocalDateTime end) {
  }
}

package com.moyuyo.service.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.constant.MembershipRuleConstants;
import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员规则配置读取：统一从 mo_system_config 读取签到/评价/邀请/补签/抵扣等规则，
 * 缺失或非法时回退到 {@link MembershipRuleConstants} 默认值。
 * 每次读取只查一次 points.* 配置，保证后台修改后 C 端规则页与发放逻辑即时生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipRuleConfigService {

  public static final String KEY_CHECKIN_POINTS = "points.checkin.points";
  public static final String KEY_CHECKIN_STREAK_DAYS = "points.checkin.streak_days";
  public static final String KEY_CHECKIN_STREAK_MULTIPLIER = "points.checkin.streak_multiplier";
  public static final String KEY_REVIEW_POINTS = "points.review.points";
  public static final String KEY_INVITE_REWARD_POINTS = "points.invite.reward_points";
  public static final String KEY_INVITE_MONTHLY_LIMIT = "points.invite.monthly_limit";
  public static final String KEY_MAKEUP_FREE_PER_MONTH = "points.makeup.free_per_month";
  public static final String KEY_MAKEUP_POINTS_COST = "points.makeup.points_cost";
  public static final String KEY_DEDUCT_POINTS_PER_UNIT = "points.deduct.points_per_unit";
  public static final String KEY_DEDUCT_MAX_PERCENT = "points.deduct.max_percent";
  public static final String KEY_RANK_BONUS_1 = "points.invite.rank_bonus_1";
  public static final String KEY_RANK_BONUS_2 = "points.invite.rank_bonus_2";
  public static final String KEY_RANK_BONUS_3 = "points.invite.rank_bonus_3";
  public static final String KEY_RANK_BONUS_4_TO_10 = "points.invite.rank_bonus_4_10";

  private final SystemConfigMapper systemConfigMapper;

  public MembershipRuleConfig getConfig() {
    Map<String, String> values = loadPointConfigs();

    int checkinPoints = positiveInt(values, KEY_CHECKIN_POINTS, MembershipRuleConstants.CHECKIN_POINTS);
    int checkinStreakDays = positiveInt(values, KEY_CHECKIN_STREAK_DAYS, MembershipRuleConstants.CHECKIN_STREAK_DAYS);
    int checkinStreakMultiplier = positiveInt(values, KEY_CHECKIN_STREAK_MULTIPLIER,
        MembershipRuleConstants.CHECKIN_STREAK_MULTIPLIER);
    int reviewPoints = positiveInt(values, KEY_REVIEW_POINTS, MembershipRuleConstants.REVIEW_POINTS);
    int inviteRewardPoints = positiveInt(values, KEY_INVITE_REWARD_POINTS,
        MembershipRuleConstants.INVITE_REWARD_POINTS);
    int inviteMonthlyLimit = positiveInt(values, KEY_INVITE_MONTHLY_LIMIT,
        MembershipRuleConstants.INVITE_MONTHLY_LIMIT);
    int makeupFreePerMonth = nonNegativeInt(values, KEY_MAKEUP_FREE_PER_MONTH,
        MembershipRuleConstants.MAKEUP_FREE_PER_MONTH);
    int makeupPointsCost = positiveInt(values, KEY_MAKEUP_POINTS_COST,
        MembershipRuleConstants.MAKEUP_POINTS_COST);
    int pointsPerCurrencyUnit = positiveInt(values, KEY_DEDUCT_POINTS_PER_UNIT,
        MembershipRuleConstants.POINTS_PER_CURRENCY_UNIT);
    // 抵扣比例按百分数配置（1~100），超出范围时回退默认 30%
    int maxDeductPercent = clamp(positiveInt(values, KEY_DEDUCT_MAX_PERCENT, 30), 1, 100);
    BigDecimal maxDeductRate = BigDecimal.valueOf(maxDeductPercent)
        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

    // 月度邀请排行奖励：允许配置为 0（表示该名次不发奖励）
    int rankBonus1 = nonNegativeInt(values, KEY_RANK_BONUS_1,
        MembershipRuleConstants.MONTHLY_RANK_BONUS_1);
    int rankBonus2 = nonNegativeInt(values, KEY_RANK_BONUS_2,
        MembershipRuleConstants.MONTHLY_RANK_BONUS_2);
    int rankBonus3 = nonNegativeInt(values, KEY_RANK_BONUS_3,
        MembershipRuleConstants.MONTHLY_RANK_BONUS_3);
    int rankBonus4To10 = nonNegativeInt(values, KEY_RANK_BONUS_4_TO_10,
        MembershipRuleConstants.MONTHLY_RANK_BONUS_4_TO_10);

    return new MembershipRuleConfig(
        checkinPoints, checkinStreakDays, checkinStreakMultiplier, reviewPoints,
        inviteRewardPoints, inviteMonthlyLimit, makeupFreePerMonth, makeupPointsCost,
        pointsPerCurrencyUnit, maxDeductPercent, maxDeductRate,
        rankBonus1, rankBonus2, rankBonus3, rankBonus4To10);
  }

  private Map<String, String> loadPointConfigs() {
    Map<String, String> map = new HashMap<>();
    try {
      List<SystemConfigEntity> list = systemConfigMapper.selectList(
          new LambdaQueryWrapper<SystemConfigEntity>()
              .likeRight(SystemConfigEntity::getConfigKey, "points."));
      for (SystemConfigEntity entity : list) {
        if (entity.getConfigKey() != null && entity.getConfigValue() != null) {
          map.put(entity.getConfigKey(), entity.getConfigValue());
        }
      }
    } catch (Exception e) {
      log.warn("[membership-rule] 读取规则配置失败，使用默认值: {}", e.getMessage());
    }
    return map;
  }

  private int positiveInt(Map<String, String> values, String key, int defaultValue) {
    int value = nonNegativeInt(values, key, defaultValue);
    return value > 0 ? value : defaultValue;
  }

  private int nonNegativeInt(Map<String, String> values, String key, int defaultValue) {
    String raw = values.get(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      return value >= 0 ? value : defaultValue;
    } catch (NumberFormatException e) {
      log.warn("[membership-rule] 配置项非法，使用默认值: key={}, value={}", key, raw);
      return defaultValue;
    }
  }

  private int clamp(int value, int min, int max) {
    return Math.min(Math.max(value, min), max);
  }

  /**
   * 会员规则快照：一次读取，供调用方在单次业务中复用。
   */
  public record MembershipRuleConfig(
      int checkinPoints,
      int checkinStreakDays,
      int checkinStreakMultiplier,
      int reviewPoints,
      int inviteRewardPoints,
      int inviteMonthlyLimit,
      int makeupFreePerMonth,
      int makeupPointsCost,
      int pointsPerCurrencyUnit,
      int maxDeductPercent,
      BigDecimal maxDeductRate,
      int rankBonus1,
      int rankBonus2,
      int rankBonus3,
      int rankBonus4To10) {
  }
}

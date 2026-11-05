package com.moyuyo.common.constant;

import java.math.BigDecimal;

/**
 * 会员/积分规则常量：会员规则页与实际发放逻辑共用同一份数值，避免页面文案与后端实现漂移。
 * 注意：等级门槛/倍率、购物返积分汇率与首单倍率等属于后台可配置项，不在此处硬编码。
 */
public final class MembershipRuleConstants {

  /** 每日签到基础积分 */
  public static final int CHECKIN_POINTS = 5;
  /** 连续签到达到该天数后享受加倍奖励 */
  public static final int CHECKIN_STREAK_DAYS = 7;
  /** 连续签到奖励倍数 */
  public static final int CHECKIN_STREAK_MULTIPLIER = 2;

  /** 撰写评价奖励积分（每个订单商品仅一次） */
  public static final int REVIEW_POINTS = 10;

  /** 邀请好友首单奖励（邀请人与被邀请人各得） */
  public static final int INVITE_REWARD_POINTS = 200;
  /** 单个邀请人每自然月最多计奖的邀请数 */
  public static final int INVITE_MONTHLY_LIMIT = 20;

  /** 每月免费补签次数 */
  public static final int MAKEUP_FREE_PER_MONTH = 1;
  /** 超出免费次数后每次补签消耗积分 */
  public static final int MAKEUP_POINTS_COST = 50;

  /** 积分抵扣换算：多少积分 = 1 个货币单位（美元） */
  public static final int POINTS_PER_CURRENCY_UNIT = 100;
  /** 单笔订单积分抵扣上限比例（商品小计） */
  public static final BigDecimal MAX_POINTS_DEDUCT_RATE = new BigDecimal("0.3");

  /** 积分有效期（月） */
  public static final int POINTS_VALID_MONTHS = 12;

  /** 月度邀请排行阶梯奖励：第 1/2/3 名与第 4-10 名 */
  public static final int MONTHLY_RANK_BONUS_1 = 1000;
  public static final int MONTHLY_RANK_BONUS_2 = 700;
  public static final int MONTHLY_RANK_BONUS_3 = 500;
  public static final int MONTHLY_RANK_BONUS_4_TO_10 = 200;

  private MembershipRuleConstants() {
  }
}

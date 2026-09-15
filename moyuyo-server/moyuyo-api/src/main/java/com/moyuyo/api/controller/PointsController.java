package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.PointsExchangeEntity;
import com.moyuyo.dao.entity.PointsGoodsEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.PointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "积分管理")
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointsController {

  /** 连续签到判定的回溯天数（从昨天起往前算，不含今天） */
  private static final int STREAK_LOOKBACK_DAYS = 7;

  private final MemberService memberService;
  private final MissionService missionService;
  private final PointsService pointsService;

  @Operation(summary = "获取积分流水")
  @GetMapping("/log")
  public Result<Page<PointsLogEntity>> getPointsLog(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(memberService.getPointsLog(UserContextHolder.getUserId(), page, size));
  }

  @Operation(summary = "获取积分余额")
  @GetMapping("/balance")
  public Result<Integer> getBalance() {
    return Result.success(memberService.getPointsBalance(UserContextHolder.getUserId()));
  }

  /**
   * 章节 2.1：每日签到 +5；连续 7 天奖励 ×2 倍率。
   * 返回今日获得积分、是否连续、累计连续天数。
   * 同一天重复签到会被拒绝。
   */
  @Operation(summary = "签到得积分")
  @PostMapping("/checkin")
  public Result<Map<String, Object>> checkin() {
    Long userId = UserContextHolder.getUserId();
    LocalDate today = LocalDate.now();

    // 连续签到判定统一走"按日期查签到流水"（回溯 7 天 + 今天），
    // 不再用"最近 N 条积分流水"分页窗口：任务奖励/订单等流水会把签到记录挤出窗口，
    // 导致重复签到校验失效、连续天数漏判，并进而让"连续签到 30 天"成就被误重置
    Set<LocalDate> checkinDates = memberService.getRecentCheckinDates(userId, STREAK_LOOKBACK_DAYS + 1);
    if (checkinDates.contains(today)) {
      throw new IllegalStateException("今日已签到，请明天再来");
    }

    int streak = 0;
    LocalDate cursor = today.minusDays(1);
    for (int i = 0; i < STREAK_LOOKBACK_DAYS; i++) {
      if (!checkinDates.contains(cursor)) {
        break;
      }
      streak++;
      cursor = cursor.minusDays(1);
    }
    int consecutiveDays = streak + 1;
    int basePoints = 5;
    int finalPoints = consecutiveDays >= 7 ? basePoints * 2 : basePoints;

    String remark = consecutiveDays >= 7
        ? "每日签到 +" + finalPoints + "（连续 7 天双倍奖励）"
        : "每日签到 +" + finalPoints + "（连续 " + consecutiveDays + " 天）";
    memberService.addPoints(userId, finalPoints, "CHECKIN", null, remark);

    // 触发任务进度：每日签到 +1；周累计签到/连续签到成就在签到周期内累计
    missionService.incrementByKeyword(userId, "DAILY", "签到", 1);
    // 本周累计签到（含今天）
    missionService.incrementByKeyword(userId, "WEEKLY", "累计签到", 1);
    // 连续签到 30 天成就：consecutiveDays==1 表示昨天没签到（断签），
    // 此时把进度拉回今天这一次，保证"连续"语义；否则按 +1 累加
    if (consecutiveDays <= 1) {
      missionService.setProgressByKeyword(userId, "ACHIEVEMENT", "连续签到 30", 1);
    } else {
      missionService.incrementByKeyword(userId, "ACHIEVEMENT", "连续签到 30", 1);
    }

    Map<String, Object> result = new java.util.HashMap<>();
    result.put("points", finalPoints);
    result.put("consecutiveDays", consecutiveDays);
    result.put("doubleReward", consecutiveDays >= 7);
    return Result.success(result);
  }

  /**
   * 章节 2.1：漏签补签（每月 1 次免费，之后 50 积分/次）。
   * 只能补"当月、今天之前且当天未签到"的日期；不传 date 时自动补当月最近的漏签日。
   * 补签流水会回填到被补签那一天，因此当天仍可正常签到，连续天数也能接上。
   */
  @Operation(summary = "漏签补签")
  @PostMapping("/checkin/makeup")
  public Result<Map<String, Object>> makeupCheckin(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return Result.success(pointsService.makeupCheckin(UserContextHolder.getUserId(), date));
  }

  /**
   * 章节 2.1：签到日历。
   * 返回 { month, dates:[yyyy-MM-dd...], checkedToday, streak }，month 省略时取当月。
   * 前端日历与"连续签到天数"直接用它渲染，不再自行按最近 50 条流水推算。
   */
  @Operation(summary = "签到日历")
  @GetMapping("/checkin/calendar")
  public Result<Map<String, Object>> checkinCalendar(@RequestParam(required = false) String month) {
    // 手工解析而非直接用 YearMonth 形参：Spring 的 JSR-310 注解格式化器不支持 YearMonth 绑定
    YearMonth target = null;
    if (month != null && !month.isBlank()) {
      try {
        target = YearMonth.parse(month);
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException("月份格式应为 yyyy-MM");
      }
    }
    return Result.success(pointsService.getCheckinCalendar(UserContextHolder.getUserId(), target));
  }

  /** 章节 3.2：积分商城礼品列表 */
  @Operation(summary = "积分商城礼品列表")
  @GetMapping("/goods")
  public Result<List<PointsGoodsEntity>> listGoods(@RequestParam(required = false) String category) {
    return Result.success(pointsService.listGoods(category));
  }

  /** 章节 3.2：兑换积分礼品 */
  @Operation(summary = "兑换积分礼品")
  @PostMapping("/goods/exchange")
  public Result<PointsExchangeEntity> exchange(@RequestBody ExchangeRequest request) {
    Long userId = UserContextHolder.getUserId();
    return Result.success(pointsService.exchange(
        userId, request.getGoodsId(),
        request.getReceiverName(), request.getReceiverPhone(), request.getReceiverAddress()));
  }

  /** 我的兑换记录 */
  @Operation(summary = "我的兑换记录")
  @GetMapping("/goods/exchanges")
  public Result<List<PointsExchangeEntity>> myExchanges() {
    return Result.success(pointsService.listMyExchanges(UserContextHolder.getUserId()));
  }

  // ----- DTO -----
  @lombok.Data
  public static class ExchangeRequest {
    private Long goodsId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
  }
}
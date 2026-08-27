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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "积分管理")
@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointsController {

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

    Page<PointsLogEntity> recent = memberService.getPointsLog(userId, 1, 50);
    LocalDate today = LocalDate.now();
    boolean alreadyCheckedToday = recent.getRecords().stream()
        .anyMatch(l -> "CHECKIN".equalsIgnoreCase(l.getType())
            && l.getCreatedAt() != null
            && l.getCreatedAt().toLocalDate().equals(today));
    if (alreadyCheckedToday) {
      throw new IllegalStateException("今日已签到，请明天再来");
    }

    int streak = 0;
    LocalDate cursor = today.minusDays(1);
    for (int i = 0; i < 7; i++) {
      final LocalDate day = cursor;
      boolean hasCheckin = recent.getRecords().stream()
          .anyMatch(l -> "CHECKIN".equalsIgnoreCase(l.getType())
              && l.getCreatedAt() != null
              && l.getCreatedAt().toLocalDate().equals(day));
      if (hasCheckin) {
        streak++;
        cursor = cursor.minusDays(1);
      } else {
        break;
      }
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
    // 连续签到 30 天成就：按 +1 累加，连续天数另由 stats 接口统计显示
    missionService.incrementByKeyword(userId, "ACHIEVEMENT", "连续签到 30", 1);

    Map<String, Object> result = new java.util.HashMap<>();
    result.put("points", finalPoints);
    result.put("consecutiveDays", consecutiveDays);
    result.put("doubleReward", consecutiveDays >= 7);
    return Result.success(result);
  }

  /** 章节 2.1：漏签补签（每月 1 次免费，之后 50 积分/次） */
  @Operation(summary = "漏签补签")
  @PostMapping("/checkin/makeup")
  public Result<Map<String, Object>> makeupCheckin() {
    return Result.success(pointsService.makeupCheckin(UserContextHolder.getUserId()));
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
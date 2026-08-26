package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.MissionEntity;
import com.moyuyo.dao.entity.UserMissionEntity;
import com.moyuyo.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "任务中心")
@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {

  private final MissionService missionService;

  @Operation(summary = "获取所有任务")
  @GetMapping
  public Result<List<MissionEntity>> list() {
    return Result.success(missionService.listAllMissions());
  }

  /**
   * 前端期望结构 {daily: [...], weekly: [...], achievements: [...]}。
   * 同时首次调用会为用户初始化 user_mission 记录。
   */
  @Operation(summary = "分组获取任务列表")
  @GetMapping("/grouped")
  public Result<Map<String, Object>> grouped() {
    return Result.success(missionService.listGroupedMissions(UserContextHolder.getUserId()));
  }

  @Operation(summary = "领取任务奖励")
  @PostMapping("/{id}/claim")
  public Result<Void> claimReward(@PathVariable Long id) {
    missionService.claimReward(UserContextHolder.getUserId(), id);
    return Result.success();
  }

  @Operation(summary = "我的任务状态")
  @GetMapping("/my")
  public Result<List<UserMissionEntity>> myMissions() {
    return Result.success(missionService.listUserMissions(UserContextHolder.getUserId()));
  }

  @Operation(summary = "任务统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(missionService.getMissionStats(UserContextHolder.getUserId()));
  }
}
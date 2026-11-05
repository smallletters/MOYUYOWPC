package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.InviteEntity;
import com.moyuyo.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "邀请好友")
@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class InviteController {

  private final InviteService inviteService;

  @Operation(summary = "获取我的邀请码")
  @GetMapping("/code")
  public Result<String> getInviteCode() {
    return Result.success(inviteService.getInviteCode(UserContextHolder.getUserId()));
  }

  @Operation(summary = "邀请统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(inviteService.getInviteStats(UserContextHolder.getUserId()));
  }

  @Operation(summary = "邀请历史记录")
  @GetMapping("/history")
  public Result<IPage<InviteEntity>> history(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return Result.success(inviteService.getInviteHistory(UserContextHolder.getUserId(), page, size));
  }

  @Operation(summary = "本月邀请排行")
  @GetMapping("/ranking")
  public Result<Map<String, Object>> ranking() {
    return Result.success(inviteService.getMonthlyRanking(UserContextHolder.getUserId()));
  }
}
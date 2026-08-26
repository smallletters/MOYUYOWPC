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

  /** 当前用户绑定邀请码（注册场景） */
  @Operation(summary = "绑定被邀请人")
  @PostMapping("/bind")
  public Result<InviteEntity> bind(@RequestParam("code") String code) {
    return Result.success(inviteService.bindInvitee(code, UserContextHolder.getUserId()));
  }

  /** 标记首单完成（订单完成回调） */
  @Operation(summary = "标记邀请完成（首单）")
  @PostMapping("/mark-ordered")
  public Result<InviteEntity> markOrdered(@RequestParam("code") String code) {
    return Result.success(inviteService.markOrdered(code, UserContextHolder.getUserId()));
  }
}
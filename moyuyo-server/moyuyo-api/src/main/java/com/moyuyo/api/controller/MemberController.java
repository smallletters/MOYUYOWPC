package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.member.MemberVO;
import com.moyuyo.common.dto.member.WalletVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "会员管理")
@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;

  @Operation(summary = "获取会员信息（含积分和钱包余额）")
  @GetMapping
  public Result<MemberVO> getMemberInfo() {
    return Result.success(memberService.getMemberInfo(UserContextHolder.getUserId()));
  }

  @Operation(summary = "获取积分流水")
  @GetMapping("/points-log")
  public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsLogEntity>> getPointsLog(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(memberService.getPointsLog(UserContextHolder.getUserId(), page, size));
  }

  @Operation(summary = "获取钱包余额")
  @GetMapping("/wallet")
  public Result<WalletVO> getWallet() {
    return Result.success(memberService.getWallet(UserContextHolder.getUserId()));
  }

  @Operation(summary = "会员等级档位（含积分倍率）")
  @GetMapping("/levels")
  public Result<List<Map<String, Object>>> listLevels() {
    return Result.success(memberService.listLevels());
  }

  @Operation(summary = "当前用户积分倍率")
  @GetMapping("/points-rate")
  public Result<Double> currentPointsRate() {
    return Result.success(memberService.getCurrentPointsRate(UserContextHolder.getUserId()));
  }
}

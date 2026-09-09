package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.prime.PrimePlanVO;
import com.moyuyo.common.dto.prime.PrimeStatusVO;
import com.moyuyo.common.dto.prime.PrimeSubscribeRequest;
import com.moyuyo.common.dto.prime.PrimeSubscribeVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.PrimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Prime 会员")
@RestController
@RequestMapping("/api/v1/prime")
@RequiredArgsConstructor
public class PrimeController {

  private final PrimeService primeService;

  @Operation(summary = "套餐列表（按 sort_order 升序）")
  @GetMapping("/plans")
  public Result<List<PrimePlanVO>> plans() {
    return Result.success(primeService.listPlans());
  }

  @Operation(summary = "当前用户的 Prime 订阅状态（已登录返回 active/plan/expireAt）")
  @GetMapping("/status")
  public Result<PrimeStatusVO> status() {
    Long userId = UserContextHolder.getUserId();
    return Result.success(primeService.getStatus(userId));
  }

  @Operation(summary = "订阅 Prime（未配支付密钥时模拟直开；配了密钥则返回 Stripe Checkout 支付地址，由 webhook 激活）")
  @PostMapping("/subscribe")
  public Result<PrimeSubscribeVO> subscribe(@Valid @RequestBody PrimeSubscribeRequest body) {
    Long userId = UserContextHolder.getUserId();
    if (userId == null) {
      return Result.error(401, "请先登录");
    }
    return Result.success(primeService.subscribe(
        userId,
        body.getPlanCode(),
        body.getPayChannel(),
        body.getClientType(),
        body.getSchemeBase(),
        body.getReturnUrl()));
  }

  @Operation(summary = "取消订阅（标记 CANCELLED，已开通视图调用）")
  @PostMapping("/cancel")
  public Result<Void> cancel() {
    Long userId = UserContextHolder.getUserId();
    if (userId == null) {
      return Result.error(401, "请先登录");
    }
    primeService.cancel(userId);
    return Result.success();
  }
}
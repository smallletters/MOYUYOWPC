package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.subscribe.AutoRenewRequest;
import com.moyuyo.common.dto.subscribe.SubscribeRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.SubscribePlanEntity;
import com.moyuyo.dao.entity.SubscriptionHistoryEntity;
import com.moyuyo.dao.entity.UserSubscriptionEntity;
import com.moyuyo.service.SubscribeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "订阅管理")
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscribeController {

  private final SubscribeService subscribeService;

  @Operation(summary = "订阅方案列表")
  @GetMapping("/plans")
  public Result<List<SubscribePlanEntity>> getPlans() {
    return Result.success(subscribeService.getPlans());
  }

  @Operation(summary = "创建订阅订单（待支付）")
  @PostMapping("/subscribe")
  public Result<OrderEntity> subscribe(@Valid @RequestBody SubscribeRequest request) {
    return Result.success(
        subscribeService.createSubscribeOrder(UserContextHolder.getUserId(), request.getPlanId()));
  }

  @Operation(summary = "我的订阅")
  @GetMapping("/my")
  public Result<UserSubscriptionEntity> getMySubscription() {
    return Result.success(subscribeService.getMySubscription(UserContextHolder.getUserId()));
  }

  @Operation(summary = "更新自动续费")
  @PutMapping("/auto-renew")
  public Result<Void> updateAutoRenew(@Valid @RequestBody AutoRenewRequest request) {
    subscribeService.updateAutoRenew(UserContextHolder.getUserId(), request.getAutoRenew());
    return Result.success();
  }

  @Operation(summary = "订阅历史")
  @GetMapping("/history")
  public Result<List<SubscriptionHistoryEntity>> getHistory() {
    return Result.success(subscribeService.getHistory(UserContextHolder.getUserId()));
  }
}

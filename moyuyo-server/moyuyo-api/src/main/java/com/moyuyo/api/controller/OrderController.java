package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.order.CancelOrderRequest;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.service.OrderService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Tag(name = "订单管理")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @Operation(summary = "创建订单")
  @PostMapping
  @RateLimiter(name = "orderCreate", fallbackMethod = "createOrderRateLimitFallback")
  public Result<OrderEntity> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    Long userId = UserContextHolder.getUserId();
    OrderEntity order = orderService.createOrderFromRequest(userId, request);
    return Result.success(order);
  }

  /** 订单创建限流降级方法 */
  @SuppressWarnings("unused")
  private Result<OrderEntity> createOrderRateLimitFallback(CreateOrderRequest request, RequestNotPermitted e) {
    return Result.error(429, "请求过于频繁，请稍后再试");
  }

  @Operation(summary = "获取订单列表")
  @GetMapping
  public Result<IPage<OrderEntity>> listOrders(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status) {
    Long userId = UserContextHolder.getUserId();
    // 关键修复：分页参数统一守卫，size 超过 100 一律截断，规避 size=100000 触发的 OOM / 全表扫描
    int[] pageParams = PageParamGuard.normalize(page, size, 10);
    IPage<OrderEntity> orderPage = orderService.listOrders(userId, pageParams[0], pageParams[1], status);

    // P1 性能修复：批量填充订单项，消除 N+1 查询（每订单一次 getOrderItems → size=100 时 101 次 SQL）
    // 改为单次批量 IN 查询 + groupingBy 归一，性能提升 10~50 倍
    List<OrderEntity> records = orderPage.getRecords();
    if (!records.isEmpty()) {
      List<Long> orderIds = new ArrayList<>(records.size());
      for (OrderEntity order : records) {
        if (order.getId() != null) {
          orderIds.add(order.getId());
        }
      }
      if (!orderIds.isEmpty()) {
        Map<Long, List<OrderItemEntity>> itemMap =
            orderService.getOrderItemsByOrderIds(orderIds);
        for (OrderEntity order : records) {
          order.setItems(itemMap.getOrDefault(order.getId(), Collections.emptyList()));
        }
      }
    }

    return Result.success(orderPage);
  }

  @Operation(summary = "获取订单详情")
  @GetMapping("/{id}")
  public Result<OrderEntity> getOrderDetail(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    OrderEntity order = orderService.getOrderDetail(id, userId);
    if (order == null) {
      return Result.error(400, "订单不存在");
    }
    order.setItems(orderService.getOrderItems(id));
    return Result.success(order);
  }

  @Operation(summary = "取消订单")
  @PostMapping("/{id}/cancel")
  public Result<Void> cancelOrder(@PathVariable Long id, @RequestBody CancelOrderRequest request) {
    Long userId = UserContextHolder.getUserId();
    orderService.cancelOrder(id, userId, request.getReason());
    return Result.success();
  }

  @Operation(summary = "确认收货")
  @PostMapping("/{id}/confirm")
  public Result<Void> confirmReceived(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    orderService.confirmReceived(id, userId);
    return Result.success();
  }

  @Operation(summary = "删除订单")
  @DeleteMapping("/{id}")
  public Result<Void> deleteOrder(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    orderService.deleteOrder(id, userId);
    return Result.success();
  }
}

package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 订单管理")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

  private final OrderService orderService;

  @Operation(summary = "订单列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status) {
    try {
      // 管理后台查看所有订单，userId 传 null
      return Result.success(orderService.listOrders(null, page, size, status));
    } catch (Exception e) {
      return Result.error("查询订单列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "订单详情")
  @GetMapping("/{id}")
  public Result<?> detail(@PathVariable Long id) {
    try {
      // 管理后台按订单ID查询详情，userId 传 null
      OrderEntity order = orderService.getOrderDetail(id, null);
      order.setItems(orderService.getOrderItems(id));
      return Result.success(order);
    } catch (Exception e) {
      return Result.error("查询订单详情失败: " + e.getMessage());
    }
  }
}

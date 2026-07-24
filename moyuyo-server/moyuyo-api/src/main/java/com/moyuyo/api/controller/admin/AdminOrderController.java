package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.LogisticsService;
import com.moyuyo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Tag(name = "管理后台 - 订单管理")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

  private final OrderService orderService;
  private final OrderMapper orderMapper;
  private final LogisticsService logisticsService;

  @Operation(summary = "订单列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {
    try {
      // 管理后台查看所有订单，支持关键字搜索和日期范围筛选
      LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
          .eq(status != null && !status.isEmpty(), OrderEntity::getStatus, status)
          .and(keyword != null && !keyword.isEmpty(), kw ->
              kw.like(OrderEntity::getOrderNo, keyword))
          .ge(startDate != null && !startDate.isEmpty(), OrderEntity::getCreateTime, LocalDate.parse(startDate).atStartOfDay())
          .le(endDate != null && !endDate.isEmpty(), OrderEntity::getCreateTime, LocalDate.parse(endDate).atTime(LocalTime.MAX))
          .orderByDesc(OrderEntity::getCreateTime);
      Page<OrderEntity> pageResult = orderMapper.selectPage(new Page<>(page, size), wrapper);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("list", pageResult.getRecords());
      result.put("total", pageResult.getTotal());
      result.put("page", pageResult.getCurrent());
      result.put("size", pageResult.getSize());
      return Result.success(result);
    } catch (Exception e) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("list", Collections.emptyList());
      result.put("total", 0);
      result.put("page", page);
      result.put("size", size);
      return Result.success(result);
    }
  }

  @Operation(summary = "订单详情")
  @GetMapping("/{id}")
  public Result<?> detail(@PathVariable Long id) {
    // 管理后台按订单ID查询详情，userId 传 null
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error("订单不存在");
    }
    order.setItems(orderService.getOrderItems(id));
    return Result.success(order);
  }

  @Operation(summary = "修改收货地址")
  @PutMapping("/{id}/address")
  public Result<Map<String, Object>> updateAddress(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      OrderEntity order = orderService.getOrderDetail(id, null);
      if (order == null) {
        return Result.error("订单不存在");
      }
      if (body.get("shippingName") != null) {
        order.setReceiverName((String) body.get("shippingName"));
      }
      if (body.get("shippingPhone") != null) {
        order.setReceiverPhone((String) body.get("shippingPhone"));
      }
      if (body.get("shippingAddress") != null) {
        order.setReceiverAddress((String) body.get("shippingAddress"));
      }
      orderMapper.updateById(order);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "地址修改成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("修改地址失败: " + e.getMessage());
    }
  }

  @Operation(summary = "确认发货")
  @PutMapping("/{id}/ship")
  public Result<Map<String, Object>> ship(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    try {
      // 验证订单是否存在
      OrderEntity order = orderService.getOrderDetail(id, null);
      if (order == null) {
        return Result.error("订单不存在");
      }

      // 提取物流信息
      String carrier = body != null && body.get("carrier") != null
        ? (String) body.get("carrier") : "默认承运商";
      String trackingNo = body != null && body.get("trackingNo") != null
        ? (String) body.get("trackingNo") : "";

      // 通过LogisticsService处理发货，会创建物流记录、更新订单状态
      logisticsService.shipOrder(id, carrier, trackingNo);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "发货成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("确认发货失败: " + e.getMessage());
    }
  }
}

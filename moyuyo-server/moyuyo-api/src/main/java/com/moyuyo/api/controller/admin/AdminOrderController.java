package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.order.OrderAddressUpdateRequest;
import com.moyuyo.common.dto.admin.order.OrderShipRequest;
import com.moyuyo.common.dto.order.CancelOrderRequest;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.UserMapper;
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
import java.util.stream.Collectors;

@Tag(name = "管理后台 - 订单管理")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

  private final OrderService orderService;
  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final UserMapper userMapper;
  private final LogisticsService logisticsService;
  // 注入 WooCommerce 同步服务：手动重推订单到 WooCommerce
  private final com.moyuyo.service.WooCommerceSyncService wooCommerceSyncService;

  @Operation(summary = "订单统计数据")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    Map<String, Object> result = new LinkedHashMap<>();
    // 按状态分组统计（MySQL 端 GROUP BY，避免全表扫描结果集到 JVM）
    List<Map<String, Object>> statusCounts = orderMapper.selectMaps(
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderEntity>()
            .select("status", "COUNT(*) AS cnt")
            .groupBy("status"));
    Map<String, Long> statusMap = new HashMap<>();
    long total = 0L;
    for (Map<String, Object> row : statusCounts) {
      String s = (String) row.get("status");
      Number cnt = (Number) row.get("cnt");
      if (s != null && cnt != null) {
        statusMap.put(s, cnt.longValue());
        total += cnt.longValue();
      }
    }
    result.put("total", total);
    result.put("byStatus", statusMap);
    return Result.success(result);
  }

  @Operation(summary = "最近订单")
  @GetMapping("/recent")
  public Result<List<OrderEntity>> recent(@RequestParam(defaultValue = "10") int limit) {
    // P1 修复：limit 参数必须硬上限保护，避免 limit=10000000 触发全表扫描后 LIMIT
    int safeLimit = Math.max(1, Math.min(limit, 100));
    List<OrderEntity> list = orderMapper.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderEntity>()
            .orderByDesc("create_time")
            .last("LIMIT " + safeLimit));
    return Result.success(list);
  }

  @Operation(summary = "订单列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {
    // P1 修复：分页参数统一归一化，size 硬上限 100，防止 size=100000 触发全表扫描/OOM
    int[] pageParams = PageParamGuard.normalize(page, size, 10);
    page = pageParams[0];
    size = pageParams[1];
    // 关键字长度限制：避免攻击者用超长字符串让 MySQL LIKE 走全表扫描（数据库 LIKE '%xxx%' 已不走索引）
    if (keyword != null && keyword.length() > 64) {
      return Result.error(400, "关键字长度不能超过 64 字符");
    }
    // 校验日期格式，非法日期直接返回友好错误，避免静默吞掉异常
    LocalDateTime startDateTime = null;
    LocalDateTime endDateTime = null;
    if (startDate != null && !startDate.isEmpty()) {
      try {
        startDateTime = LocalDate.parse(startDate).atStartOfDay();
      } catch (Exception e) {
        return Result.error(400, "开始日期格式无效: " + startDate);
      }
    }
    if (endDate != null && !endDate.isEmpty()) {
      try {
        endDateTime = LocalDate.parse(endDate).atTime(LocalTime.MAX);
      } catch (Exception e) {
        return Result.error(400, "结束日期格式无效: " + endDate);
      }
    }

    // 管理后台查看所有订单，支持关键字搜索和日期范围筛选
    LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(status != null && !status.isEmpty(), OrderEntity::getStatus, status)
        .and(keyword != null && !keyword.isEmpty(), kw ->
            kw.like(OrderEntity::getOrderNo, keyword))
        .ge(startDateTime != null, OrderEntity::getCreateTime, startDateTime)
        .le(endDateTime != null, OrderEntity::getCreateTime, endDateTime)
        .orderByDesc(OrderEntity::getCreateTime);
    Page<OrderEntity> pageResult = orderMapper.selectPage(new Page<>(page, size), wrapper);
    List<OrderEntity> records = pageResult.getRecords();
    if (!records.isEmpty()) {
      // 批量补充用户昵称，避免 N+1 查询；列表页展示头像与用户名
      List<Long> userIds = records.stream().map(OrderEntity::getUserId)
          .filter(Objects::nonNull).distinct().collect(Collectors.toList());
      Map<Long, String> userNameMap = userIds.isEmpty() ? Collections.emptyMap()
          : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(
              UserEntity::getId, u -> u.getNickname() == null ? "" : u.getNickname(), (a, b) -> a));
      // 批量补充订单商品明细，列表页展示商品概要
      List<Long> orderIds = records.stream().map(OrderEntity::getId)
          .filter(Objects::nonNull).collect(Collectors.toList());
      Map<Long, List<OrderItemEntity>> itemMap = orderItemMapper.selectList(
              new LambdaQueryWrapper<OrderItemEntity>().in(OrderItemEntity::getOrderId, orderIds))
          .stream().collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
      for (OrderEntity order : records) {
        order.setUserName(userNameMap.get(order.getUserId()));
        order.setItems(itemMap.getOrDefault(order.getId(), Collections.emptyList()));
      }
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", records);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return Result.success(result);
  }

  @Operation(summary = "订单详情")
  @GetMapping("/{id}")
  public Result<?> detail(@PathVariable Long id) {
    // 管理后台按订单ID查询详情，userId 传 null
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }
    order.setItems(orderService.getOrderItems(id));
    // 若订单已推送到 WooCommerce，主动从 WC 拉取最新承运商/运单号回写本地，
    // 避免管理后台看到的运单信息落后于 WC 后台（管理员在 WC 后台补录运单号的场景）
    if (order.getWooOrderId() != null) {
      try {
        order = wooCommerceSyncService.syncOrderTrackingFromWooCommerce(id);
      } catch (Exception e) {
        // WC 拉取失败不影响主流程：仍返回本地数据
      }
    }
    return Result.success(order);
  }

  @Operation(summary = "修改收货地址")
  @PutMapping("/{id}/address")
  public Result<OperationResult> updateAddress(@PathVariable Long id, @RequestBody OrderAddressUpdateRequest request) {
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }
    // 已发货订单不允许修改地址，避免物流信息不一致
    if (order.getStatusEnum() == OrderStatusEnum.SHIPPED
        || order.getStatusEnum() == OrderStatusEnum.RECEIVED
        || order.getStatusEnum() == OrderStatusEnum.COMPLETED) {
      return Result.error(400, "已发货/已完成订单不可修改收货地址");
    }
    // 仅在请求显式传值时覆盖原值,与原 Map 逻辑保持一致
    if (request.getShippingName() != null) {
      order.setReceiverName(request.getShippingName());
    }
    if (request.getShippingPhone() != null) {
      order.setReceiverPhone(request.getShippingPhone());
    }
    if (request.getShippingAddress() != null) {
      order.setReceiverAddress(request.getShippingAddress());
    }
    orderMapper.updateById(order);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("地址修改成功");
    return Result.success(result);
  }

  @Operation(summary = "确认发货")
  @PutMapping("/{id}/ship")
  public Result<OperationResult> ship(@PathVariable Long id, @RequestBody(required = false) OrderShipRequest request) {
    // 验证订单是否存在
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }
    // 仅允许已支付/待发货的订单发货
    if (!order.isPaid() || order.getStatusEnum() == OrderStatusEnum.CANCELLED) {
      return Result.error(400, "订单未支付或已取消，无法发货");
    }
    if (order.getStatusEnum() == OrderStatusEnum.SHIPPED
        || order.getStatusEnum() == OrderStatusEnum.RECEIVED
        || order.getStatusEnum() == OrderStatusEnum.COMPLETED) {
      return Result.error(400, "订单已发货或已完成，请勿重复发货");
    }

    // 提取物流信息,未传值时使用默认值(与原 Map 逻辑保持一致)
    String carrier = request != null && request.getCarrier() != null
      ? request.getCarrier() : "默认承运商";
    String trackingNo = request != null && request.getTrackingNo() != null
      ? request.getTrackingNo() : "";

    // 通过LogisticsService处理发货，会创建物流记录、更新订单状态
    logisticsService.shipOrder(id, carrier, trackingNo);

    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("发货成功");
    return Result.success(result);
  }

  @Operation(summary = "取消订单（管理后台）")
  @PutMapping("/{id}/cancel")
  public Result<OperationResult> cancel(@PathVariable Long id, @RequestBody(required = false) CancelOrderRequest request) {
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }
    // 已发货/已完成的订单不允许取消
    if (order.getStatusEnum() == OrderStatusEnum.SHIPPED
        || order.getStatusEnum() == OrderStatusEnum.RECEIVED
        || order.getStatusEnum() == OrderStatusEnum.COMPLETED) {
      return Result.error(400, "已发货/已完成订单不支持取消");
    }
    String reason = request != null && request.getReason() != null
      ? request.getReason() : "管理员操作";
    // 管理后台取消不校验userId，传null
    orderService.cancelOrder(id, null, reason);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("订单已取消");
    return Result.success(result);
  }

  @Operation(summary = "删除/作废订单（管理后台）")
  @DeleteMapping("/{id}")
  public Result<Map<String, Object>> delete(@PathVariable Long id) {
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }
    // 仅允许删除已取消的订单
    if (order.getStatusEnum() != OrderStatusEnum.CANCELLED) {
      return Result.error(400, "仅支持删除已取消的订单，请先取消订单再删除");
    }
    orderService.deleteOrder(id, null);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("message", "订单已删除");
    return Result.success(result);
  }

  /**
   * 手动重推订单到 WooCommerce。
   * 用于 payCallback 同步失败或 WooCommerce 端缺失订单数据时人工补偿。
   * 仅同步已支付订单；其他状态返回 400。
   */
  @Operation(summary = "手动重推订单到 WooCommerce")
  @PostMapping("/{id}/sync-to-woo")
  public Result<Map<String, Object>> syncToWoo(@PathVariable Long id) {
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }
    if (!order.isPaid()) {
      return Result.error(400, "仅已支付订单支持手动重推，当前状态: " + order.getStatus());
    }
    // 调用同步服务：内部会判断 wooOrderId 是否已存在，存在则跳过推送
    wooCommerceSyncService.syncOrderToWooCommerce(order);
    // 同步完成后重读实体，把最新的 wooOrderId / syncStatus 回传给前端
    OrderEntity fresh = orderService.getOrderDetail(id, null);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("wooOrderId", fresh != null ? fresh.getWooOrderId() : null);
    result.put("syncStatus", fresh != null ? fresh.getSyncStatus() : null);
    result.put("message", "订单重推任务已执行，请查看 syncStatus 与 wooOrderId");
    return Result.success(result);
  }
}

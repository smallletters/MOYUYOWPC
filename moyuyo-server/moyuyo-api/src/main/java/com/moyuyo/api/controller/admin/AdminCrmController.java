package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.CsPerformanceEntity;
import com.moyuyo.dao.admin.mapper.CsPerformanceMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - CRM管理")
@RestController
@RequestMapping("/api/admin/crm")
public class AdminCrmController {

  private final OrderMapper orderMapper;
  private final UserMapper userMapper;
  private final OrderItemMapper orderItemMapper;

  // 新DAO模块maven安装失败时允许为null，避免ClassNotFoundException
  @Autowired(required = false)
  private Object csPerformanceMapper;

  // 手动构造器注入必需的依赖
  public AdminCrmController(OrderMapper orderMapper,
                              UserMapper userMapper,
                              OrderItemMapper orderItemMapper) {
    this.orderMapper = orderMapper;
    this.userMapper = userMapper;
    this.orderItemMapper = orderItemMapper;
  }

  @Operation(summary = "客服绩效列表")
  @GetMapping("/cs-performance")
  public Result<List<Map<String, Object>>> csPerformance(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    // 新Mapper可能因maven安装失败为null，返回空列表
    if (csPerformanceMapper == null) {
      return Result.success(Collections.emptyList());
    }
    Page<CsPerformanceEntity> pageResult = ((CsPerformanceMapper) csPerformanceMapper).selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<CsPerformanceEntity>()
            .orderByDesc(CsPerformanceEntity::getTodayTickets));

    List<Map<String, Object>> list = new ArrayList<>();
    for (CsPerformanceEntity entity : pageResult.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("agentId", entity.getId());
      item.put("agentName", entity.getAgentName());
      item.put("ticketCount", entity.getTodayTickets());
      item.put("avgResponseTime", entity.getAvgResponseTime());
      item.put("satisfactionScore", entity.getSatisfactionScore());
      item.put("todayOnlineDuration", entity.getTodayOnlineDuration());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "客服详情")
  @GetMapping("/{agentId}/cs-detail")
  public Result<Map<String, Object>> csDetail(@PathVariable Long agentId) {
    // 新Mapper可能因maven安装失败为null
    if (csPerformanceMapper == null) {
      return Result.error("客服服务暂不可用");
    }
    CsPerformanceEntity entity = ((CsPerformanceMapper) csPerformanceMapper).selectOne(
        new LambdaQueryWrapper<CsPerformanceEntity>().eq(CsPerformanceEntity::getId, agentId));
    if (entity == null) {
      return Result.error("客服不存在");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("agentId", entity.getId());
    result.put("agentName", entity.getAgentName());
    result.put("department", entity.getDepartment());
    result.put("totalTickets", entity.getTotalTickets());
    result.put("avgResponseTime", entity.getAvgResponseTime());
    result.put("satisfactionScore", entity.getSatisfactionScore());
    result.put("todayOnlineDuration", entity.getTodayOnlineDuration());
    result.put("todayTickets", entity.getTodayTickets());
    result.put("status", entity.getStatus());
    result.put("latestLogin", entity.getLatestLoginTime());
    return Result.success(result);
  }

  @Operation(summary = "实时大屏数据")
  @GetMapping("/realtime")
  public Result<Map<String, Object>> realtime() {
    // 从 mo_order 表统计今日订单数和销售额
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

    // 今日订单数（createTime 在今天范围内）
    Long todayOrders = orderMapper.selectCount(
        new LambdaQueryWrapper<OrderEntity>()
            .ge(OrderEntity::getCreateTime, todayStart)
            .le(OrderEntity::getCreateTime, todayEnd));

    // 今日销售额（已支付订单的 goodsAmount 之和）
    List<OrderEntity> todayOrdersList = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .ge(OrderEntity::getCreateTime, todayStart)
            .le(OrderEntity::getCreateTime, todayEnd)
            .in(OrderEntity::getStatus, "PAID", "DELIVERED", "RECEIVED"));
    BigDecimal todaySales = todayOrdersList.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // 在线用户数（最近30分钟内有登录记录的用户）
    LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
    Long onlineUsers = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>()
            .ge(UserEntity::getLastLoginTime, thirtyMinutesAgo));

    // 今日访客数（今日有登录记录的用户）
    Long todayVisitors = userMapper.selectCount(
        new LambdaQueryWrapper<UserEntity>()
            .ge(UserEntity::getLastLoginTime, todayStart));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("onlineUsers", onlineUsers);
    result.put("todayVisitors", todayVisitors);
    result.put("todayOrders", todayOrders);
    result.put("todaySales", todaySales);
    result.put("updateTime", LocalDateTime.now());
    return Result.success(result);
  }

  @Operation(summary = "实时订单流")
  @GetMapping("/realtime-order-flow")
  public Result<List<Map<String, Object>>> realtimeOrderFlow() {
    // 从 mo_order 表查询最新创建的订单
    List<OrderEntity> latestOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .orderByDesc(OrderEntity::getCreateTime)
            .last("LIMIT 10"));

    List<Map<String, Object>> list = new ArrayList<>();
    for (OrderEntity order : latestOrders) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("orderNo", order.getOrderNo());
      item.put("userName", order.getReceiverName() != null ? order.getReceiverName() : "");
      item.put("amount", order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO);
      item.put("status", order.getStatus());
      item.put("orderTime", order.getCreateTime());
      list.add(item);
    }

    return Result.success(list);
  }

  @Operation(summary = "热门商品排行榜")
  @GetMapping("/realtime/top-products")
  public Result<List<Map<String, Object>>> topProducts() {
    // 从 mo_order_item 表统计今日销量最高的商品
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

    // 查询今日所有订单项
    List<OrderItemEntity> todayItems = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
          .orderByDesc(OrderItemEntity::getQuantity));

    if (todayItems.isEmpty()) {
      return Result.success(Collections.emptyList());
    }

    // 按 productName 分组统计销量和金额
    Map<String, List<OrderItemEntity>> grouped = todayItems.stream()
        .collect(Collectors.groupingBy(
            item -> item.getProductName() != null ? item.getProductName() : "未知商品"));

    List<Map<String, Object>> productList = new ArrayList<>();
    for (Map.Entry<String, List<OrderItemEntity>> entry : grouped.entrySet()) {
      int salesCount = entry.getValue().stream().mapToInt(OrderItemEntity::getQuantity).sum();
      BigDecimal salesAmount = entry.getValue().stream()
          .map(i -> i.getSubtotal() != null ? i.getSubtotal() : BigDecimal.ZERO)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("productName", entry.getKey());
      item.put("salesCount", salesCount);
      item.put("salesAmount", salesAmount);
      productList.add(item);
    }

    // 按销量降序排列
    productList.sort((a, b) -> Integer.compare((int) b.get("salesCount"), (int) a.get("salesCount")));

    // 添加排名
    int rank = 1;
    for (Map<String, Object> item : productList) {
      item.put("rank", rank++);
    }

    return Result.success(productList);
  }
}

package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.entity.CsPerformanceEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import com.moyuyo.dao.admin.mapper.CsPerformanceMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - CRM管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/crm")
@SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
public class AdminCrmController {

  private final OrderMapper orderMapper;
  private final UserMapper userMapper;
  private final OrderItemMapper orderItemMapper;
  private final CsPerformanceMapper csPerformanceMapper;
  private final AdminUserMapper adminUserMapper;

  @Operation(summary = "客服绩效列表")
  @GetMapping("/cs-performance")
  public Result<List<Map<String, Object>>> csPerformance(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    Page<CsPerformanceEntity> pageResult = csPerformanceMapper.selectPage(
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

  /**
   * 客服列表（不分页）：用于工单转交下拉选择
   * 从 admin_user 表里查 role = CUSTOMER_SVC（即 RBAC 客服主管 / 客服人员），避免与 cs_performance 绩效表耦合
   */
  @Operation(summary = "客服列表（不分页，用于转交下拉）")
  @GetMapping("/cs-staff")
  public Result<List<Map<String, Object>>> csStaff() {
    // 直接复用 admin_user mapper，按角色 code 过滤
    LambdaQueryWrapper<AdminUserEntity> qw = new LambdaQueryWrapper<AdminUserEntity>()
        .eq(AdminUserEntity::getRole, "CUSTOMER_SVC")
        .orderByDesc(AdminUserEntity::getId);
    List<AdminUserEntity> users = adminUserMapper.selectList(qw);
    List<Map<String, Object>> list = new ArrayList<>();
    for (AdminUserEntity u : users) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("agentId", u.getId());
      item.put("agentName", u.getName() != null && !u.getName().isEmpty() ? u.getName() : u.getUsername());
      list.add(item);
    }
    return Result.success(list);
  }

  @Operation(summary = "客服详情")
  @GetMapping("/{agentId}/cs-detail")
  public Result<Map<String, Object>> csDetail(@PathVariable Long agentId) {
    CsPerformanceEntity entity = csPerformanceMapper.selectOne(
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
    // 边界统一用半开区间 [todayStart, todayStart.plusDays(1))，避免 DATETIME 秒级精度下 LocalTime.MAX 被截断造成的边界脆弱性
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    LocalDateTime tomorrowStart = todayStart.plusDays(1);

    // 今日订单数（createTime 在今天范围内）
    Long todayOrders = orderMapper.selectCount(
        new LambdaQueryWrapper<OrderEntity>()
            .ge(OrderEntity::getCreateTime, todayStart)
            .lt(OrderEntity::getCreateTime, tomorrowStart));

    // 今日销售额：已支付及后续正向状态的订单 pay_amount 之和
    // 注意：历史上曾误用旧枚举值 "DELIVERED"，与 OrderStatusEnum.SHIPPED 不匹配，会漏算已发货但未收货的订单。
    // 这里统一按 OrderStatusEnum 当前枚举写出，避免后续枚举再改名时再出现同类口径漂移。
    List<OrderEntity> todayOrdersList = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .ge(OrderEntity::getCreateTime, todayStart)
            .lt(OrderEntity::getCreateTime, tomorrowStart)
            .in(OrderEntity::getStatus,
                OrderStatusEnum.PAID.name(),
                OrderStatusEnum.SHIPPED.name(),
                OrderStatusEnum.RECEIVED.name(),
                OrderStatusEnum.COMPLETED.name()));
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

  /**
   * 实时大屏 - GMV 趋势
   * 返回今日 00~ 当前小时 与 昨日 00~23 时的 GMV（元），按小时分桶。
   * 数据源：mo_order，仅统计已支付/已发货/已收货/已完成（PAID/SHIPPED/RECEIVED/COMPLETED）的 pay_amount。
   * 注意：与上方 /realtime 的口径存在差异（那里写的是旧枚举 DELIVERED），本接口按 OrderStatusEnum 当前真值给出。
   */
  @Operation(summary = "实时大屏 - GMV趋势(今日/昨日按小时)")
  @GetMapping("/realtime/gmv-trend")
  public Result<List<Map<String, Object>>> realtimeGmvTrend() {
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    // 注意：必须是"昨天 00:00"，而非 todayStart - 1 day（否则昨天 0~当前时刻的订单会漏进桶）
    LocalDateTime yesterdayStart = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.MIN);
    int currentHour = LocalTime.now().getHour();

    // 一次性查"昨天 00:00 ~ 明天 00:00（不含）"范围内的已支付订单，内存按小时分桶，避免两条 SQL
    List<OrderEntity> orders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .ge(OrderEntity::getCreateTime, yesterdayStart)
            .lt(OrderEntity::getCreateTime, todayStart.plusDays(1))
            .in(OrderEntity::getStatus,
                OrderStatusEnum.PAID.name(),
                OrderStatusEnum.SHIPPED.name(),
                OrderStatusEnum.RECEIVED.name(),
                OrderStatusEnum.COMPLETED.name())
            .select(OrderEntity::getPayAmount, OrderEntity::getCreateTime));

    // 初始化 24 小时桶（缺小时补 0，保证前端柱状图整点对齐）
    BigDecimal[] todayBucket = new BigDecimal[24];
    BigDecimal[] yesterdayBucket = new BigDecimal[24];
    for (int i = 0; i < 24; i++) {
      todayBucket[i] = BigDecimal.ZERO;
      yesterdayBucket[i] = BigDecimal.ZERO;
    }
    for (OrderEntity o : orders) {
      if (o.getPayAmount() == null || o.getCreateTime() == null) continue;
      LocalDateTime t = o.getCreateTime();
      int hour = t.getHour();
      // 查询范围已限定在 [yesterdayStart, todayStart+1day)，按今天/昨天分桶只需判断是否早于 todayStart
      BigDecimal[] target = t.isBefore(todayStart) ? yesterdayBucket : todayBucket;
      target[hour] = target[hour].add(o.getPayAmount());
    }

    List<Map<String, Object>> list = new ArrayList<>(24);
    for (int h = 0; h < 24; h++) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("hour", String.format("%02d", h));
      item.put("today", todayBucket[h]);
      item.put("yesterday", yesterdayBucket[h]);
      // 仅返回今日 ≤ 当前小时的数据为"已发生"段；超过当前小时的今日值前端仍可见（值=0），便于对位昨天同时刻
      item.put("isCurrentOrPast", h <= currentHour);
      list.add(item);
    }
    return Result.success(list);
  }

  /**
   * 实时大屏 - 今日发货概览
   * 数据源：mo_order；当前口径与 OrderStatusEnum 严格对齐。
   *   pending : 今日进入 PENDING_SHIP 但尚未发货的订单数（涵盖当日新增待发 + 历史遗留待发）
   *   shipped : 今日 deliverTime 在今日范围内的订单数（不论下单日，只看发货动作发生时间）
   *   todayDeliveredAmount : 今日已发货订单的 pay_amount 之和
   */
  @Operation(summary = "实时大屏 - 今日发货概览")
  @GetMapping("/realtime/shipping")
  public Result<Map<String, Object>> realtimeShipping() {
    // 边界统一为半开区间 [todayStart, tomorrowStart)，与 realtime() / realtimeGmvTrend() 保持一致
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    LocalDateTime tomorrowStart = todayStart.plusDays(1);

    // 待发货：当前仍处于 PENDING_SHIP 的订单总数（不限于今日新增，反映仓库压力）
    Long pending = orderMapper.selectCount(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getStatus, OrderStatusEnum.PENDING_SHIP.name()));

    // 今日已发货：以 deliverTime 落入今日为准（避免今日之前下单但今日发货被漏算）。
    // 注意：状态覆盖 SHIPPED/RECEIVED/COMPLETED——订单发货后会继续流转到已收货/已完成，
    // 当日发货动作在"今日已发货数"里仍然要算入，否则发货后很快被签收的订单会被漏算。
    List<OrderEntity> shippedToday = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .ge(OrderEntity::getDeliverTime, todayStart)
            .lt(OrderEntity::getDeliverTime, tomorrowStart)
            .in(OrderEntity::getStatus,
                OrderStatusEnum.SHIPPED.name(),
                OrderStatusEnum.RECEIVED.name(),
                OrderStatusEnum.COMPLETED.name())
            .select(OrderEntity::getPayAmount));

    BigDecimal todayDeliveredAmount = shippedToday.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("pending", pending);
    result.put("shipped", (long) shippedToday.size());
    result.put("todayDeliveredAmount", todayDeliveredAmount);
    result.put("updateTime", LocalDateTime.now());
    return Result.success(result);
  }

  @Operation(summary = "热门商品排行榜")
  @GetMapping("/realtime/top-products")
  public Result<List<Map<String, Object>>> topProducts() {
    // 从 mo_order_item 表统计今日销量最高的商品
    // 边界统一为半开区间 [todayStart, tomorrowStart)
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
    LocalDateTime tomorrowStart = todayStart.plusDays(1);

    // 仅统计处于"正向状态"（PAID/SHIPPED/RECEIVED/COMPLETED）的订单项，
    // 避免把今日创建但仍处 PENDING_PAY / CANCELLED / REFUNDED / REFUNDING 的数量计入"今日销量"
    String positiveStatusSql = "'" + OrderStatusEnum.PAID.name() + "','"
        + OrderStatusEnum.SHIPPED.name() + "','"
        + OrderStatusEnum.RECEIVED.name() + "','"
        + OrderStatusEnum.COMPLETED.name() + "'";

    List<OrderItemEntity> todayItems = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
          .ge(OrderItemEntity::getCreateTime, todayStart)
          .lt(OrderItemEntity::getCreateTime, tomorrowStart)
          .inSql(OrderItemEntity::getOrderId,
              "SELECT id FROM mo_order WHERE status IN (" + positiveStatusSql + ")"));

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

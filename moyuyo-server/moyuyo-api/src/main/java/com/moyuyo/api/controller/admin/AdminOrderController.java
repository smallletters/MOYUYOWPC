package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.order.OrderAddressUpdateRequest;
import com.moyuyo.common.dto.admin.order.OrderLogisticsRequest;
import com.moyuyo.common.dto.admin.order.OrderLogisticsVO;
import com.moyuyo.common.dto.admin.order.OrderShipRequest;
import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.common.dto.order.CancelOrderRequest;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.LogisticsService;
import com.moyuyo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AdminOrderController {

  private final OrderService orderService;
  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final ProductSkuMapper productSkuMapper;
  private final UserMapper userMapper;
  private final LogisticsService logisticsService;
  // 物流弹窗写回 mo_logistics 使用（与 logisticsService 查询结果共享同一张表）
  private final com.moyuyo.dao.mapper.LogisticsMapper logisticsMapper;
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
    List<OrderItemEntity> items = orderService.getOrderItems(id);

    // ==================== 管理后台订单详情视图所需字段补全 ====================
    // 1) 按 skuId 或 productId 批量查 mo_product_sku → 填入 items[*].skuCode (transient 字段)
    //    - 变体商品：item.skuId 对应 mo_product_sku 的主键 id → 取对应 sku_code
    //    - 简单商品：item.skuId 可能是默认 SKU 行主键，或 productId；
    //      如果 skuId 在 map 里找不到，再按 product_id 找该商品的唯一默认 SKU
    if (items != null && !items.isEmpty()) {
      // 先收集所有可能的 SKU 条件：(1) item.skuId (2) item.productId
      List<Long> allSkuIds = new ArrayList<>();
      List<Long> allProductIds = new ArrayList<>();
      for (OrderItemEntity item : items) {
        if (item.getSkuId() != null) allSkuIds.add(item.getSkuId());
        if (item.getProductId() != null) allProductIds.add(item.getProductId());
      }
      // key1: skuId -> skuCode（覆盖变体商品 skuId 已知的场景）
      Map<Long, String> skuIdToCode = new HashMap<>();
      // key2: productId -> 默认 skuCode（简单商品单条默认 SKU 的兜底）
      Map<Long, String> productIdToCode = new HashMap<>();
      if (!allProductIds.isEmpty()) {
        // 先按 product_id 批量拉所有关联 SKU 行；简单商品只有 1 条，变体商品多条
        List<ProductSkuEntity> skusByProduct = productSkuMapper.selectList(
            new LambdaQueryWrapper<ProductSkuEntity>().in(ProductSkuEntity::getProductId, allProductIds));
        // 第一次遍历：按 productId 的"第一条（通常就是 simple 品默认）"记录兜底
        Map<Long, List<ProductSkuEntity>> groupByProduct = skusByProduct.stream()
            .collect(Collectors.groupingBy(ProductSkuEntity::getProductId));
        groupByProduct.forEach((pid, list) -> {
          if (list != null && !list.isEmpty()) {
            // 简单商品：列表长度=1 → 就是默认 SKU
            // 变体商品：列表>1，这里兜底填"第一个"变体的 SKU；下面会再被 skuId 精确覆盖
            productIdToCode.put(pid, list.get(0).getSkuCode());
          }
        });
        // 第二次遍历：按 sku.id -> skuCode 精确匹配（变体商品 item.skuId 必定命中）
        for (ProductSkuEntity sku : skusByProduct) {
          if (sku.getId() != null) {
            skuIdToCode.put(sku.getId(), sku.getSkuCode());
          }
        }
      }
      // 对每个 item 先按 skuId 精确查，再按 productId 兜底
      for (OrderItemEntity item : items) {
        String code = null;
        if (item.getSkuId() != null) code = skuIdToCode.get(item.getSkuId());
        if (code == null && item.getProductId() != null) code = productIdToCode.get(item.getProductId());
        item.setSkuCode(code);
      }
    }
    order.setItems(items);

    // 2) 填充 payMethodName（渠道 + 细分方法的友好名）
    //    当前订单主表只有 payChannel 字段（STRIPE/PAYPAL），mo_payment 表有 payMethod。
    //    为避免 N+1 与跨模块耦合，按 payChannel 先映射成渠道级友好名；
    //    后续如果订单主表存了 pay_method，可再进一步细化到 Google Pay / Venmo / Apple Pay 等
    order.setPayMethodName(resolvePayMethodName(order.getPayChannel()));

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

  /**
   * 将 payChannel 字符串映射为管理后台订单详情「支付方式」列的友好展示名。
   * 目前订单主表只有 payChannel（STRIPE / PAYPAL），没有细粒度 payMethod。
   * 如果后续在 mo_order 里增加了 pay_method 字段，可在 detail 中把两个字段组合映射。
   */
  private String resolvePayMethodName(String payChannel) {
    if (payChannel == null || payChannel.isBlank()) return "待支付";
    switch (payChannel.trim().toUpperCase()) {
      case "PAYPAL":     return "PayPal / Venmo";
      case "STRIPE":     return "Stripe（Apple Pay / Google Pay / Alipay / 卡支付）";
      case "ALIPAY":     return "支付宝 Alipay";
      case "WECHAT":     return "微信支付";
      case "CASH_APP":   return "Cash App";
      default:           return payChannel;
    }
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

  // ==================== 物流弹窗：查询 / 修改 承运商与运单号 + 展示轨迹 ====================

  @Operation(summary = "查询订单物流详情（弹窗使用：基础信息 + 轨迹）")
  @GetMapping("/{id}/logistics")
  public Result<OrderLogisticsVO> getOrderLogistics(@PathVariable Long id) {
    // 订单基础信息（含 addressId 回查地址）
    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }
    // 读取 mo_logistics（可能为空：未发货或发货链路未走 mo_logistics）
    com.moyuyo.dao.entity.LogisticsEntity logistics = null;
    try {
      logistics = logisticsService.getLogisticsByOrderId(id);
    } catch (Exception e) {
      // 查询失败不影响返回基础信息
    }

    OrderLogisticsVO vo = new OrderLogisticsVO();
    vo.setOrderId(order.getId());
    vo.setOrderNo(order.getOrderNo());
    vo.setOrderStatus(order.getStatus());

    // 优先取 mo_logistics.carrier / trackingNumber / shippedAt / receivedAt / traces，
    // 再用 mo_order 的发货字段兜底（兼容只走 /orders/{id}/ship 链路的历史订单）。
    if (logistics != null) {
      vo.setCarrier(firstNonBlank(logistics.getCarrier(), order.getShippingCarrier()));
      vo.setTrackingNumber(firstNonBlank(logistics.getTrackingNumber(), order.getTrackingNumber()));
      vo.setShippedAt(logistics.getShippedAt() != null ? logistics.getShippedAt().toString()
          : order.getDeliverTime() != null ? order.getDeliverTime().toString() : null);
      vo.setReceivedAt(logistics.getReceivedAt() != null ? logistics.getReceivedAt().toString()
          : order.getReceivedTime() != null ? order.getReceivedTime().toString() : null);
      vo.setTraces(parseTraces(logistics.getTraces()));
    } else {
      vo.setCarrier(order.getShippingCarrier());
      vo.setTrackingNumber(order.getTrackingNumber());
      vo.setShippedAt(order.getDeliverTime() != null ? order.getDeliverTime().toString() : null);
      vo.setReceivedAt(order.getReceivedTime() != null ? order.getReceivedTime().toString() : null);
      vo.setTraces(java.util.Collections.emptyList());
    }

    // 计算当前状态（若调用第三方快递查询接口可用其状态覆盖，此处做本地兜底）
    String status = computeLogisticsStatus(order, logistics, vo.getTraces());
    vo.setCurrentStatus(status);
    vo.setCurrentStatusLabel(logisticsStatusLabel(status));

    // 如果没有任何轨迹，但已经有运单号和承运商，则生成一条"待揽收/商家已交运"的占位轨迹，
    // 保证弹窗下半部分永远有内容可读（等接入快递100/17track后用真实轨迹覆盖）。
    if ((vo.getTraces() == null || vo.getTraces().isEmpty())
        && vo.getTrackingNumber() != null && !vo.getTrackingNumber().isEmpty()) {
      LogisticsVO.TraceItem placeHolder = new LogisticsVO.TraceItem();
      placeHolder.setTime(vo.getShippedAt() != null ? vo.getShippedAt() : LocalDateTime.now().toString());
      placeHolder.setLocation("");
      placeHolder.setDesc(buildPlaceholderDesc(vo.getCarrier(), vo.getTrackingNumber()));
      placeHolder.setStatus("info_received");
      vo.setTraces(java.util.List.of(placeHolder));
    }
    return Result.success(vo);
  }

  @Operation(summary = "更新订单物流信息（手动设置承运商 / 运单号，未发货可顺带发货）")
  @PutMapping("/{id}/logistics")
  public Result<Map<String, Object>> updateOrderLogistics(@PathVariable Long id,
      @RequestBody(required = false) OrderLogisticsRequest request) {
    if (request == null) {
      return Result.error(400, "参数为空");
    }
    // 校验运单号：如果运单号非空，限制长度，避免脏数据写入
    String trackingNo = request.getTrackingNo() == null ? "" : request.getTrackingNo().trim();
    if (trackingNo.length() > 64) {
      return Result.error(400, "运单号长度不能超过 64 字符");
    }
    String carrier = request.getCarrier() == null ? "" : request.getCarrier().trim();
    if (carrier.length() > 64) {
      return Result.error(400, "承运商名称长度不能超过 64 字符");
    }

    OrderEntity order = orderService.getOrderDetail(id, null);
    if (order == null) {
      return Result.error(404, "订单不存在");
    }

    boolean forceShip = Boolean.TRUE.equals(request.getForceShip());
    boolean alreadyShipped = OrderStatusEnum.SHIPPED.name().equals(order.getStatus())
        || OrderStatusEnum.RECEIVED.name().equals(order.getStatus())
        || OrderStatusEnum.COMPLETED.name().equals(order.getStatus());

    // forceShip=true 且订单尚未发货：走统一发货链路（会创建 mo_logistics 并改订单状态）
    if (forceShip && !alreadyShipped) {
      if (!order.isPaid()) {
        return Result.error(400, "订单未支付，无法直接发货，请先完成支付或取消勾选「设置后立即发货」");
      }
      String safeCarrier = carrier.isEmpty() ? "默认承运商" : carrier;
      logisticsService.shipOrder(id, safeCarrier, trackingNo);
    } else {
      // 仅更新承运商 / 运单号（不改变订单状态），同时写回 mo_order 和已存在的 mo_logistics
      order.setShippingCarrier(carrier.isEmpty() ? order.getShippingCarrier() : carrier);
      order.setTrackingNumber(trackingNo.isEmpty() ? order.getTrackingNumber() : trackingNo);
      orderMapper.updateById(order);

      // 如果有对应的 mo_logistics 记录，一并同步更新（保持 1:1 一致）
      try {
        com.moyuyo.dao.entity.LogisticsEntity logistics = logisticsService.getLogisticsByOrderId(id);
        if (logistics != null) {
          if (!carrier.isEmpty()) logistics.setCarrier(carrier);
          if (!trackingNo.isEmpty()) logistics.setTrackingNumber(trackingNo);
          logisticsMapper.updateById(logistics);
        }
      } catch (Exception e) {
        // logistics 同步失败不影响订单表结果，仅记录日志
        log.warn("[order-logistics] 同步 mo_logistics 失败，orderId={}", id, e);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("carrier", carrier);
    result.put("trackingNumber", trackingNo);
    result.put("shipped", forceShip && !alreadyShipped);
    result.put("message", forceShip && !alreadyShipped ? "发货成功，运单信息已保存" : "运单信息已保存");
    return Result.success(result);
  }

  /**
   * 取第一个非空字符串（与 Obj 工具等价，这里直接内联实现，避免引入额外依赖）。
   */
  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b;
  }

  /** 根据订单/物流表/轨迹 计算一个稳定的物流状态英文枚举值 */
  private String computeLogisticsStatus(OrderEntity order,
      com.moyuyo.dao.entity.LogisticsEntity logistics,
      List<LogisticsVO.TraceItem> traces) {
    if (logistics != null && logistics.getReceivedAt() != null) return "DELIVERED";
    if (order.getReceivedTime() != null) return "DELIVERED";
    boolean hasTracking = ((logistics != null && logistics.getTrackingNumber() != null
        && !logistics.getTrackingNumber().isEmpty())
        || (order.getTrackingNumber() != null && !order.getTrackingNumber().isEmpty()));
    boolean hasTrace = traces != null && traces.size() >= 2;
    boolean shipped = (logistics != null && logistics.getShippedAt() != null)
        || order.getDeliverTime() != null
        || OrderStatusEnum.SHIPPED.name().equals(order.getStatus())
        || OrderStatusEnum.RECEIVED.name().equals(order.getStatus())
        || OrderStatusEnum.COMPLETED.name().equals(order.getStatus());
    if (!shipped) return hasTracking ? "PENDING_PICKUP" : "NO_RECORD";
    if (hasTrace) return "IN_TRANSIT";
    return "PENDING_PICKUP";
  }

  /** 物流状态英文 → 中文展示标签 */
  private String logisticsStatusLabel(String status) {
    if (status == null) return "暂无";
    return switch (status) {
      case "NO_RECORD" -> "暂无物流";
      case "PENDING_PICKUP" -> "待揽收";
      case "IN_TRANSIT" -> "运输中";
      case "OUT_FOR_DELIVERY" -> "派送中";
      case "DELIVERED" -> "已签收";
      case "EXCEPTION" -> "异常件";
      default -> status;
    };
  }

  /** 发货占位轨迹描述，和主流购物 APP 口径一致（"商家已出库/等待快递员揽收"） */
  private String buildPlaceholderDesc(String carrier, String trackingNo) {
    StringBuilder sb = new StringBuilder();
    sb.append("商家已交运");
    if (carrier != null && !carrier.isEmpty()) sb.append("，承运商：").append(carrier);
    if (trackingNo != null && !trackingNo.isEmpty()) sb.append("，运单号：").append(trackingNo);
    sb.append("，等待快递员上门揽收…");
    return sb.toString();
  }

  /** 解析 mo_logistics.traces（JSON 字符串）为前端时间轴格式；解析失败返回空列表，不抛异常 */
  private List<LogisticsVO.TraceItem> parseTraces(String tracesJson) {
    if (tracesJson == null || tracesJson.isBlank()) return java.util.Collections.emptyList();
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JavaType type = mapper.getTypeFactory()
          .constructCollectionType(List.class, LogisticsVO.TraceItem.class);
      List<LogisticsVO.TraceItem> list = mapper.readValue(tracesJson, type);
      // 期望时间倒序（最新在顶部）；如果列表里时间都带 yyyy-MM-dd 前缀，按自然序降序排一下更稳
      if (list != null) {
        list.sort((a, b) -> {
          String ta = a.getTime() == null ? "" : a.getTime();
          String tb = b.getTime() == null ? "" : b.getTime();
          return tb.compareTo(ta);
        });
      }
      return list == null ? java.util.Collections.emptyList() : list;
    } catch (Exception e) {
      log.warn("[order-logistics] traces JSON 解析失败，回退为空列表", e);
      return java.util.Collections.emptyList();
    }
  }
}

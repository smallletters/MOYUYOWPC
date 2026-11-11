package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.order.CancelOrderRequest;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.order.CreatePresaleOrderRequest;
import com.moyuyo.common.dto.aftersales.AfterSalesVO;
import com.moyuyo.common.ratelimit.RedisRateLimit;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.SubscribePlanEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.SubscribePlanMapper;
import com.moyuyo.service.AfterSalesService;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.RefundService;
import com.moyuyo.service.support.ProductAttributes;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;

@Tag(name = "订单管理")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;
  private final RefundService refundService;
  private final AfterSalesService afterSalesService;
  private final ProductMapper productMapper;
  private final SubscribePlanMapper subscribePlanMapper;

  /**
   * 分次退货：为订单各商品行填充已申请售后数量与剩余可退数量/金额。
   * <p>
   * 数据来源 mo_after_sales（排除已拒绝/已取消），APP 退款申请页据此限制数量与金额，
   * 服务端 createAfterSales 会再做一次权威校验。
   */
  private void fillAfterSalesQuota(OrderEntity order) {
    List<OrderItemEntity> items = order.getItems();
    if (items == null || items.isEmpty()) {
      return;
    }
    Map<Long, Integer> appliedQty = afterSalesService.getAppliedQuantityByOrder(
        order.getId(), order.getUserId());
    Map<Long, BigDecimal> appliedAmount = afterSalesService.getAppliedAmountByOrder(
        order.getId(), order.getUserId());
    for (OrderItemEntity item : items) {
      int bought = item.getQuantity() == null ? 0 : item.getQuantity();
      int applied = appliedQty.getOrDefault(item.getId(), 0);
      item.setAfterSalesQuantity(applied);
      item.setReturnableQuantity(Math.max(bought - applied, 0));

      BigDecimal subtotal = item.getSubtotal();
      if (subtotal == null && item.getPrice() != null) {
        subtotal = item.getPrice().multiply(BigDecimal.valueOf(bought));
      }
      BigDecimal appliedTotal = appliedAmount.getOrDefault(item.getId(), BigDecimal.ZERO);
      item.setReturnableAmount(subtotal == null ? BigDecimal.ZERO
          : subtotal.subtract(appliedTotal).max(BigDecimal.ZERO));
    }
  }

  /**
   * 填充购买须知：汇总订单内各商品 attributes.purchase_note（去重），供 APP 订单详情展示。
   */
  private void fillPurchaseNote(OrderEntity order) {
    List<OrderItemEntity> items = order.getItems();
    if (items == null || items.isEmpty()) {
      return;
    }
    List<Long> productIds = items.stream()
        .map(OrderItemEntity::getProductId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (productIds.isEmpty()) {
      return;
    }
    LinkedHashSet<String> notes = new LinkedHashSet<>();
    for (ProductEntity product : productMapper.selectBatchIds(productIds)) {
      String note = ProductAttributes.str(
          ProductAttributes.parse(product.getAttributes()), "purchase_note", "");
      if (!note.isEmpty()) {
        notes.add(note);
      }
    }
    if (!notes.isEmpty()) {
      order.setPurchaseNote(String.join("\n", notes));
    }
  }

  /**
   * 填充订阅方案名：订阅订单按 subscribePlanId 批量回查方案名，
   * 避免 APP 从 remark 字符串里解析（方案改名/备注被覆盖时会展示错误）。
   */
  private void fillSubscribePlanNames(List<OrderEntity> orders) {
    if (orders == null || orders.isEmpty()) {
      return;
    }
    List<Long> planIds = orders.stream()
        .filter(OrderEntity::isSubscription)
        .map(OrderEntity::getSubscribePlanId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (planIds.isEmpty()) {
      return;
    }
    Map<Long, String> nameMap = new HashMap<>();
    for (SubscribePlanEntity plan : subscribePlanMapper.selectBatchIds(planIds)) {
      nameMap.put(plan.getId(), plan.getName());
    }
    for (OrderEntity order : orders) {
      if (order.isSubscription() && order.getSubscribePlanId() != null) {
        order.setSubscribePlanName(nameMap.get(order.getSubscribePlanId()));
      }
    }
  }

  @Operation(summary = "创建订单")
  @PostMapping
  @RateLimiter(name = "orderCreate", fallbackMethod = "createOrderRateLimitFallback")
  // 集群级限流：与预售下单共用 order-create 配额，防止多副本下 Resilience4j 阈值被放大
  @RedisRateLimit(name = "order-create", limit = 10, windowSeconds = 60)
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

  @Operation(summary = "创建预售订单（全款/定金）")
  @PostMapping("/presale")
  @RateLimiter(name = "orderCreate", fallbackMethod = "createPresaleOrderRateLimitFallback")
  @RedisRateLimit(name = "order-create", limit = 10, windowSeconds = 60)
  public Result<OrderEntity> createPresaleOrder(@Valid @RequestBody CreatePresaleOrderRequest request) {
    Long userId = UserContextHolder.getUserId();
    OrderEntity order = orderService.createPresaleOrder(userId, request);
    return Result.success(order);
  }

  /** 预售订单创建限流降级方法 */
  @SuppressWarnings("unused")
  private Result<OrderEntity> createPresaleOrderRateLimitFallback(CreatePresaleOrderRequest request, RequestNotPermitted e) {
    return Result.error(429, "请求过于频繁，请稍后再试");
  }

  @Operation(summary = "支付尾款：为已支付定金的订单生成尾款单")
  @PostMapping("/{id}/presale-balance")
  @RateLimiter(name = "orderCreate", fallbackMethod = "createBalanceOrderRateLimitFallback")
  // 尾款单创建持行锁，集群级限流避免单用户高频请求放大锁竞争
  @RedisRateLimit(name = "order-balance", limit = 10, windowSeconds = 60)
  public Result<OrderEntity> createBalanceOrder(@PathVariable Long id) {
    Long userId = UserContextHolder.getUserId();
    OrderEntity order = orderService.createBalanceOrder(id, userId);
    return Result.success(order);
  }

  /** 支付尾款限流降级方法：尾款单创建持行锁，高频请求会放大锁竞争 */
  @SuppressWarnings("unused")
  private Result<OrderEntity> createBalanceOrderRateLimitFallback(Long id, RequestNotPermitted e) {
    return Result.error(429, "请求过于频繁，请稍后再试");
  }

  @Operation(summary = "获取订单列表")
  @GetMapping
  public Result<IPage<OrderEntity>> listOrders(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String orderType,
      @RequestParam(required = false) String excludeOrderType) {
    Long userId = UserContextHolder.getUserId();
    // 关键修复：分页参数统一守卫，size 超过 100 一律截断，规避 size=100000 触发的 OOM / 全表扫描
    int[] pageParams = PageParamGuard.normalize(page, size, 10);
    IPage<OrderEntity> orderPage = orderService.listOrders(
        userId, pageParams[0], pageParams[1], status, orderType, excludeOrderType);

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
    fillSubscribePlanNames(records);

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
    // 订阅订单填充方案名（列表/详情统一由后端返回，前端不再解析 remark）
    fillSubscribePlanNames(Collections.singletonList(order));
    // 购买须知：从商品 attributes 读取并在订单详情展示
    fillPurchaseNote(order);
    // 填充退款金额快照，供 APP 退款页展示"剩余可退金额"：
    // 整单退款实际退的是剩余可退（实付 - 已退/退款中），已部分退过时小于实付金额
    BigDecimal refunded = refundService.refundedAmount(id);
    order.setRefundedAmount(refunded);
    order.setRefundableAmount(order.getPayAmount() == null
        ? BigDecimal.ZERO
        : order.getPayAmount().subtract(refunded).max(BigDecimal.ZERO));

    // 该订单最近一条售后记录：APP 仅在存在售后时才展示"售后记录"入口
    AfterSalesVO latestAfterSales =
        afterSalesService.getLatestAfterSales(id, UserContextHolder.getUserId());
    if (latestAfterSales != null) {
      order.setAfterSalesId(latestAfterSales.getId());
      order.setAfterSalesStatus(latestAfterSales.getStatus());
    }

    // 分次退货：为每个商品行填充「已申请售后数量/金额」，APP 据此限制可退数量与金额上限
    fillAfterSalesQuota(order);
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

package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.order.CancelOrderRequest;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.aftersales.AfterSalesVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.ProductMapper;
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

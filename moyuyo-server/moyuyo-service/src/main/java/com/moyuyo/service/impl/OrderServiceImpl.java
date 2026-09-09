package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.order.OrderItemRequest;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.CouponService;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.NotificationService;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.WooCommerceSyncService;
import com.moyuyo.service.mq.NotificationMessageProducer;
import static com.moyuyo.common.enums.OrderStatusEnum.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final PaymentMapper paymentMapper;
  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;
  // 地址表 Mapper：用于创建订单时快照地址详情，以及详情接口对历史订单按 addressId 回填
  private final AddressMapper addressMapper;
  // 注入 WooCommerce 同步服务：付款回调完成后自动推送订单到 WooCommerce
  private final WooCommerceSyncService wooCommerceSyncService;
  // 任务中心埋点：付款回调后触发"完成 1 单购物 / 首单完成 / 累计消费满 500"
  private final MissionService missionService;
  private final NotificationMessageProducer notificationMessageProducer;
  private final NotificationService notificationService;
  // 消费返积分：付款回调后按 1 USD = 10 积分发放，首单 2 倍
  private final PointsRewardServiceImpl pointsRewardService;
  // 资金安全：下单优惠金额必须服务端重算（券核验/积分抵扣），不能信任前端传入数值
  private final CouponService couponService;
  private final MemberService memberService;

  @Override
  @Transactional
  public OrderEntity createOrder(Long userId, List<OrderItemEntity> items, Long addressId, String remark, String couponId) {
    // 兼容旧签名：默认无折扣、无积分抵扣、无运费
    return createOrder(userId, items, addressId, remark, couponId,
            BigDecimal.ZERO, 0, BigDecimal.ZERO, "standard", BigDecimal.ZERO);
  }

  @Override
  @Transactional
  public OrderEntity createOrder(Long userId, List<OrderItemEntity> items, Long addressId, String remark,
                                 String couponId, BigDecimal couponDiscount, Integer pointsUsed,
                                 BigDecimal pointsDiscount, String shippingMethod, BigDecimal freight) {
    // 生产防护：禁止空商品列表创建零金额订单
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("订单商品不能为空");
    }
    // 生成订单号: ORD + yyyyMMdd + 8位雪花ID后缀
    String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String snowId = String.valueOf(IdWorker.getId());
    String orderNo = "ORD" + datePart + snowId.substring(snowId.length() - 8);

    // 校验库存并计算商品总金额；M2 修复：非 SKU 商品也走原子 UPDATE 扣减 stock，防止并发超卖
    BigDecimal goodsAmount = BigDecimal.ZERO;
    for (OrderItemEntity item : items) {
      // 校验商品是否存在且在上架状态
      ProductEntity product = productMapper.selectById(item.getProductId());
      if (product == null) {
        throw new IllegalArgumentException("商品不存在: " + item.getProductId());
      }
      if (product.getOnSale() == null || !product.getOnSale()) {
        throw new IllegalArgumentException("商品已下架: " + product.getName());
      }
      if (item.getSkuId() != null) {
        // 走 SKU 原子扣减
        ProductSkuEntity sku = productSkuMapper.selectById(item.getSkuId());
        if (sku == null) {
          throw new IllegalArgumentException("SKU不存在: " + item.getSkuId());
        }
        // 原子扣减：UPDATE mo_product_sku SET stock = stock - qty WHERE id = ? AND stock >= qty
        LambdaUpdateWrapper<ProductSkuEntity> stockWrapper = new LambdaUpdateWrapper<>();
        stockWrapper.eq(ProductSkuEntity::getId, item.getSkuId())
            .setSql("stock = stock - " + item.getQuantity())
            .apply("stock >= {0}", item.getQuantity());
        int affected = productSkuMapper.update(null, stockWrapper);
        if (affected == 0) {
          throw new IllegalStateException("商品库存不足: " + product.getName());
        }
        // 下单时快照规格文本:保证管理后台订单详情能展示变体规格,后续 SKU 被删/重建也不丢失
        if (item.getSkuSpec() == null || item.getSkuSpec().isBlank()) {
          item.setSkuSpec(sku.getSpec());
        }
      } else {
        // 非 SKU 商品：M2 修复，同样走原子扣减 product.stock（防超卖）
        // 原实现只比较 product.getStock() 但不真扣减，多人并发会同时通过校验导致超卖
        LambdaUpdateWrapper<ProductEntity> productStockWrapper = new LambdaUpdateWrapper<>();
        productStockWrapper.eq(ProductEntity::getId, item.getProductId())
            .setSql("stock = stock - " + item.getQuantity())
            .apply("stock >= {0}", item.getQuantity());
        int affected = productMapper.update(null, productStockWrapper);
        if (affected == 0) {
          throw new IllegalStateException("商品库存不足: " + product.getName());
        }
      }
      goodsAmount = goodsAmount.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    // 金额兜底：null 转 0，避免 NPE 与下游 BigDecimal 计算异常
    BigDecimal safeCouponDiscount = couponDiscount == null ? BigDecimal.ZERO : couponDiscount;
    BigDecimal safePointsDiscount = pointsDiscount == null ? BigDecimal.ZERO : pointsDiscount;
    BigDecimal safeFreight = freight == null ? BigDecimal.ZERO : freight;
    int safePointsUsed = pointsUsed == null ? 0 : Math.max(pointsUsed, 0);

    // M1 修复：payAmount = 商品总金额 + 运费 - 优惠券减免 - 积分抵扣，下限 0
    // 原实现直接 payAmount = goodsAmount，导致前端展示的优惠金额与实际扣款金额不一致
    BigDecimal payAmount = goodsAmount
            .add(safeFreight)
            .subtract(safeCouponDiscount)
            .subtract(safePointsDiscount);
    if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
      payAmount = BigDecimal.ZERO;
    }

    // 构建订单
    OrderEntity order = new OrderEntity();
    order.setOrderNo(orderNo);
    order.setUserId(userId);
    order.setGoodsAmount(goodsAmount);
    order.setFreight(safeFreight);
    order.setCouponDiscount(safeCouponDiscount);
    order.setPointsDiscount(safePointsDiscount);
    order.setPointsUsed(safePointsUsed);
    order.setPayAmount(payAmount);
    order.setStatus(PENDING_PAY.name());
    order.setAddressId(addressId);
    order.setRemark(remark);
    order.setCouponId(couponId);
    order.setShippingMethod(shippingMethod == null ? "standard" : shippingMethod);
    order.setDeleteStatus(0);

    // 按 addressId 查询地址并快照收件人/电话/详细地址到订单表，
    // 防止用户后续修改或删除地址后，订单的历史收货信息丢失。
    if (addressId != null) {
      try {
        AddressEntity address = addressMapper.selectById(addressId);
        if (address != null) {
          order.setReceiverName(address.getReceiver());
          order.setReceiverPhone(address.getPhone());
          order.setReceiverAddress(formatFullAddress(address));
          order.setReceiverZip(address.getZipCode());
        }
      } catch (Exception e) {
        // 地址查询失败只记录日志，不阻断下单（避免地址表异常导致整个下单链路不可用）
        log.warn("[order] 创建订单快照地址失败，addressId={}, orderNo={}", addressId, orderNo, e);
      }
    }

    orderMapper.insert(order);

    // 批量保存订单项
    for (OrderItemEntity item : items) {
      item.setOrderId(order.getId());
      item.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
      orderItemMapper.insert(item);
    }

    return order;
  }

  @Override
  @Transactional
  public OrderEntity createOrderFromRequest(Long userId, CreateOrderRequest request) {
    // 将请求中的商品信息转换为订单项实体（含 SKU/商品校验）
    List<OrderItemEntity> items = new ArrayList();
    for (OrderItemRequest itemReq : request.getItems()) {
      ProductSkuEntity sku = productSkuMapper.selectById(itemReq.getSkuId());
      ProductEntity product = productMapper.selectById(itemReq.getProductId());
      if (product == null) {
        throw new IllegalArgumentException("商品不存在: " + itemReq.getProductId());
      }

      OrderItemEntity item = new OrderItemEntity();
      item.setProductId(itemReq.getProductId());
      item.setProductName(product.getName());
      item.setMainImage(product.getMainImage());
      item.setQuantity(itemReq.getQuantity());
      // 简单商品(simple)无独立 SKU 记录：前端将商品 id 作为 SKU id 传递，
      // SKU 查不到且 skuId 恰等于商品 id 时降级为非 SKU 商品，使用商品价格、库存扣减走商品维度
      if (sku == null && itemReq.getSkuId() != null && itemReq.getSkuId().equals(product.getId())) {
        item.setSkuId(null);
        item.setPrice(product.getPrice());
      } else if (sku == null) {
        throw new IllegalArgumentException("SKU不存在: " + itemReq.getSkuId());
      } else {
        item.setSkuId(itemReq.getSkuId());
        item.setPrice(sku.getPrice());
      }
      items.add(item);
    }

    // 服务端重算金额项（资金安全：不信任前端传入的减免数值）：
    // 1) 商品小计 = 服务端价格 × 数量
    BigDecimal goodsSubtotal = BigDecimal.ZERO;
    for (OrderItemEntity it : items) {
      goodsSubtotal = goodsSubtotal.add(
          it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
    }
    // 2) 优惠券：按 userCoupon 记录核验归属/状态/门槛并重算减免
    BigDecimal serverCouponDiscount = couponService.computeCouponDiscount(
        userId, request.getCouponUserId(), goodsSubtotal);
    // 3) 积分抵扣：100 积分 = 1 元，最高抵扣小计 30%（与前端结算页规则一致，防止伪造抵扣）
    BigDecimal serverPointsDiscount = BigDecimal.ZERO;
    int serverPointsUsed = 0;
    boolean wantsPoints = (request.getPointsUsed() != null && request.getPointsUsed() > 0)
        || (request.getPointsDiscount() != null
            && request.getPointsDiscount().compareTo(BigDecimal.ZERO) > 0);
    if (wantsPoints) {
      int balance = Math.max(memberService.getPointsBalance(userId), 0);
      BigDecimal maxByRate = goodsSubtotal.multiply(new BigDecimal("0.3"))
          .setScale(2, java.math.RoundingMode.HALF_UP);
      BigDecimal maxByBalance = BigDecimal.valueOf(balance)
          .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.DOWN);
      serverPointsDiscount = maxByRate.min(maxByBalance);
      serverPointsUsed = serverPointsDiscount.multiply(BigDecimal.valueOf(100))
          .setScale(0, java.math.RoundingMode.DOWN).intValue();
    }
    BigDecimal serverFreight = request.getFreight() == null ? BigDecimal.ZERO : request.getFreight();

    // 落库（同一事务内）并核销优惠券，避免同一张券被多笔待支付订单同时占用
    OrderEntity order = createOrder(userId, items, request.getAddressId(), request.getRemark(),
            request.getCouponId(), serverCouponDiscount,
            serverPointsUsed, serverPointsDiscount,
            request.getShippingMethod(), serverFreight);

    if (serverCouponDiscount.compareTo(BigDecimal.ZERO) > 0 && request.getCouponUserId() != null) {
        // 记录用券明细，便于取消返还与对账
        order.setUserCouponId(request.getCouponUserId());
        orderMapper.updateById(order);
        couponService.useCoupon(userId, request.getCouponUserId(), order.getId());
        log.info("订单使用优惠券已核销: orderId={}, userCouponId={}", order.getId(), request.getCouponUserId());
    }
    return order;
  }

  /**
   * 支付回调抢占失败分支：
   * - 订单已是"已支付"后续状态 → 重复回调/并发已处理，静默幂等返回；
   * - 订单被取消/拦截等异常态仍收到成功回调 → 落一笔 SUCCESS 支付流水并告警，进入人工对账，
   *   避免"钱已扣、订单已取消"被静默吞掉。
   */
  private void handlePaymentForNonPendingOrder(OrderEntity order, String payChannel, String transactionId) {
    // 用 FOR UPDATE 当前读拿最新状态（普通 select 可能读到 RR 快照里的过期 PENDING_PAY）
    OrderEntity latest = orderMapper.selectByIdForUpdate(order.getId());
    String st = latest != null ? latest.getStatus() : null;
    if (st == null) {
      log.error("[payment-reconcile] 支付成功回调但订单已不存在: orderNo={}", order.getOrderNo());
      return;
    }
    // 已支付后续状态集合（重复回调/并发已处理）；DELIVERED 仅是物流轨迹状态，非 mo_order.status，不可混用
    List<String> paidStates = List.of(PENDING_SHIP.name(), PAID.name(), SHIPPED.name(),
        RECEIVED.name(), COMPLETED.name());
    if (paidStates.contains(st)) {
      log.info("支付回调重复/已处理: orderNo={}, status={}", order.getOrderNo(), st);
      return;
    }
    // 异常态（CANCELLED/HOLD/退款中等）收到成功支付 → 必须人工对账
    log.error("[payment-reconcile] 支付成功但订单状态异常 {}，订单已被取消/拦截？orderNo={}, transactionId={}, "
        + "请人工核对渠道到账并处理退款", st, order.getOrderNo(), transactionId);
    try {
      Long exists = paymentMapper.selectCount(new LambdaQueryWrapper<PaymentEntity>()
          .eq(PaymentEntity::getOrderId, order.getId())
          .eq(PaymentEntity::getTransactionId, transactionId));
      if (exists != null && exists > 0) {
        return;
      }
      PaymentEntity payment = new PaymentEntity();
      payment.setOrderId(order.getId());
      payment.setPayChannel(payChannel);
      payment.setTransactionId(transactionId);
      payment.setAmount(latest.getPayAmount());
      payment.setStatus("SUCCESS");
      payment.setPaidAt(java.time.LocalDateTime.now());
      paymentMapper.insert(payment);
    } catch (Exception e) {
      log.warn("[payment-reconcile] 落对账流水失败: orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
    }
  }

  @Override
  public IPage<OrderEntity> listOrders(Long userId, int page, int size, String status) {
    LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getDeleteStatus, 0);
    // 管理员(userId=null)查看全部订单，不限制userId
    if (userId != null) {
        wrapper.eq(OrderEntity::getUserId, userId);
    }

    // 按状态筛选:支持单状态 eq 或逗号分隔多状态 in (前端 PENDING_SHIP tab 需查 PAID,PENDING_SHIP)
    if (status != null && !status.isEmpty()) {
      if (status.contains(",")) {
        List<String> statusList = Arrays.stream(status.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toList());
        if (!statusList.isEmpty()) {
          wrapper.in(OrderEntity::getStatus, statusList);
        }
      } else {
        wrapper.eq(OrderEntity::getStatus, status);
      }
    }

    wrapper.orderByDesc(OrderEntity::getCreateTime);

    return orderMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public OrderEntity getOrderDetail(Long orderId, Long userId) {
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) {
      // 返回 null 而非抛异常，由 controller 决定如何处理
      return null;
    }
    // 管理员(userId=null)跳过权限校验
    if (userId != null && !Objects.equals(order.getUserId(), userId)) {
      throw new IllegalArgumentException("无权访问该订单");
    }
    // 兼容历史订单：创建时还没有快照地址（receiverName/receiverPhone/receiverAddress 全空），
    // 此时按 addressId 回查 mo_address 回填显示值，保证管理后台订单详情/用户订单详情都能看到地址。
    fillAddressIfAbsent(order);
    return order;
  }

  /**
   * 将地址实体格式化为完整地址字符串（与 APP 结算页 addr-detail 显示口径一致）：
   *   详细地址 + 城市 + 州/省 + 邮编 + 国家
   * 字段都为 null/空时返回空串，不会拼出多余空格。
   */
  private String formatFullAddress(AddressEntity address) {
    if (address == null) return "";
    StringBuilder sb = new StringBuilder();
    appendPart(sb, address.getDetail());
    appendPart(sb, address.getCity());
    appendPart(sb, address.getProvince());
    appendPart(sb, address.getDistrict());
    appendPart(sb, address.getZipCode());
    appendPart(sb, address.getCountry());
    return sb.toString().trim();
  }

  /** 追加非空片段，前面自动加一个空格分隔 */
  private void appendPart(StringBuilder sb, String part) {
    if (part == null || part.isEmpty()) return;
    if (sb.length() > 0) sb.append(' ');
    sb.append(part);
  }

  /**
   * 若订单的收件人/电话/详细地址三字段都为空，且存在 addressId，
   * 则回查 mo_address 并把快照字段补齐，便于详情页与列表展示。
   * 不会回写数据库（避免触发不必要的 update），仅在内存对象上赋值。
   */
  private void fillAddressIfAbsent(OrderEntity order) {
    if (order == null) return;
    boolean hasSnapshot = isNotBlank(order.getReceiverName())
        || isNotBlank(order.getReceiverPhone())
        || isNotBlank(order.getReceiverAddress());
    if (hasSnapshot) return;
    if (order.getAddressId() == null) return;
    try {
      AddressEntity address = addressMapper.selectById(order.getAddressId());
      if (address == null) return;
      order.setReceiverName(address.getReceiver());
      order.setReceiverPhone(address.getPhone());
      order.setReceiverAddress(formatFullAddress(address));
      if (order.getReceiverZip() == null || order.getReceiverZip().isEmpty()) {
        order.setReceiverZip(address.getZipCode());
      }
    } catch (Exception e) {
      // 回填失败不影响订单详情主流程，只记录告警
      log.warn("[order] 详情回填地址快照失败，orderId={}, addressId={}",
          order.getId(), order.getAddressId(), e);
    }
  }

  private static boolean isNotBlank(String s) {
    return s != null && !s.trim().isEmpty();
  }

  @Override
  @Transactional
  public void cancelOrder(Long orderId, Long userId, String reason) {
    OrderEntity order = getOrderDetail(orderId, userId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }
    // 幂等：已取消的订单直接返回（定时任务/用户/后台可能重复触发同一单）
    if (CANCELLED.name().equals(order.getStatus())) {
      log.info("订单已取消，跳过重复取消: orderId={}", orderId);
      return;
    }

    // 原子抢占取消资格：仅 PENDING_PAY 可置为 CANCELLED。
    // 与支付回调/超时取消并发时，谁先提交谁生效，另一方不会重复回补库存、不会覆盖支付结果。
    LocalDateTime cancelTime = LocalDateTime.now();
    int claimed = orderMapper.update(null,
        new LambdaUpdateWrapper<OrderEntity>()
            .eq(OrderEntity::getId, orderId)
            .eq(OrderEntity::getStatus, PENDING_PAY.name())
            .set(OrderEntity::getStatus, CANCELLED.name())
            .set(OrderEntity::getCancelTime, cancelTime)
            .set(OrderEntity::getCancelReason, reason));
    if (claimed == 0) {
      // 竞态失败：订单已被支付/发货/取消，交由各自流程处理（FOR UPDATE 当前读最新状态）
      OrderEntity latest = orderMapper.selectByIdForUpdate(orderId);
      String latestStatus = latest != null ? latest.getStatus() : null;
      if (latestStatus != null && latestStatus.equals(CANCELLED.name())) {
        return; // 已被并发取消，视为幂等成功
      }
      throw new IllegalStateException("当前订单状态不允许取消");
    }
    order.setStatus(CANCELLED.name());
    order.setCancelTime(cancelTime);
    order.setCancelReason(reason);

    // 仅在成功抢占取消后恢复已扣减的库存，避免并发双倍回补导致超卖
    restoreStockForOrderItems(orderId);

    // 取消未支付订单：返还本次下单已核销的优惠券，供用户重新使用
    if (order.getUserCouponId() != null) {
      try {
        couponService.releaseCoupon(order.getUserCouponId(), orderId);
      } catch (Exception e) {
        log.warn("[order] 取消订单返还优惠券失败: orderId={}, userCouponId={}, reason={}",
                orderId, order.getUserCouponId(), e.getMessage());
      }
    }
  }

  /** 恢复订单占用的库存（仅允许在取消成功/特定回补场景调用，幂等由调用方保证） */
  private void restoreStockForOrderItems(Long orderId) {
    // 恢复已扣减的库存：SKU 走原子累加、无 SKU 的简单商品回补 product.stock
    List<OrderItemEntity> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
            .eq(OrderItemEntity::getOrderId, orderId));
    if (items == null) {
      return;
    }
    for (OrderItemEntity item : items) {
      if (item.getQuantity() == null || item.getQuantity() <= 0) {
        continue;
      }
      if (item.getSkuId() != null) {
        LambdaUpdateWrapper<ProductSkuEntity> restoreWrapper = new LambdaUpdateWrapper<>();
        restoreWrapper.eq(ProductSkuEntity::getId, item.getSkuId())
            .setSql("stock = stock + " + item.getQuantity());
        int affected = productSkuMapper.update(null, restoreWrapper);
        if (affected == 0) {
          log.warn("取消订单恢复库存失败：SKU不存在或已删除，skuId={}, orderId={}",
                  item.getSkuId(), orderId);
        }
      } else if (item.getProductId() != null) {
        LambdaUpdateWrapper<ProductEntity> restoreWrapper = new LambdaUpdateWrapper<>();
        restoreWrapper.eq(ProductEntity::getId, item.getProductId())
            .setSql("stock = stock + " + item.getQuantity());
        int affected = productMapper.update(null, restoreWrapper);
        if (affected == 0) {
          log.warn("取消订单恢复库存失败：商品不存在或已删除，productId={}, orderId={}",
                  item.getProductId(), orderId);
        }
      }
    }
  }

  @Override
  @Transactional
  public void payCallback(String orderNo, String payChannel, String transactionId) {
    // 查找订单
    OrderEntity order = orderMapper.selectOne(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getOrderNo, orderNo));
    if (order == null) {
      log.error("支付回调订单不存在: orderNo={}", orderNo);
      throw new IllegalArgumentException("订单不存在");
    }
    // 原子抢占：仅 PENDING_PAY 能进入"已支付"，与取消/超时取消并发时后提交方不会覆盖状态
    LocalDateTime paidAt = LocalDateTime.now();
    int claimed = orderMapper.update(null,
        new LambdaUpdateWrapper<OrderEntity>()
            .eq(OrderEntity::getId, order.getId())
            .eq(OrderEntity::getStatus, PENDING_PAY.name())
            .set(OrderEntity::getStatus, PENDING_SHIP.name())
            .set(OrderEntity::getPaidAt, paidAt)
            .set(OrderEntity::getPayChannel, payChannel)
            .set(OrderEntity::getPayTransactionId, transactionId));
    if (claimed == 0) {
      // 抢占失败：可能已被支付(重复回调)或已被取消/拦截，进入对账分支
      handlePaymentForNonPendingOrder(order, payChannel, transactionId);
      return;
    }
    // 同步内存对象，供下方任务/Woo/通知使用
    order.setStatus(PENDING_SHIP.name());
    order.setPaidAt(paidAt);
    order.setPayChannel(payChannel);
    order.setPayTransactionId(transactionId);

    // 记录支付流水
    PaymentEntity payment = new PaymentEntity();
    payment.setOrderId(order.getId());
    payment.setPayChannel(payChannel);
    payment.setTransactionId(transactionId);
    payment.setAmount(order.getPayAmount());
    payment.setStatus("SUCCESS");
    payment.setPaidAt(LocalDateTime.now());
    paymentMapper.insert(payment);

    log.info("支付回调处理成功: orderNo={}, transactionId={}", orderNo, transactionId);

    // 积分抵扣闭环：支付成功后才真正扣减下单时计算的抵扣积分(pointsUsed)。
    // 余额不足等异常不阻断支付(钱已到账)，仅告警并交给对账/后续人工处理；
    // 抢占式状态更新保证本段只执行一次，不会重复扣减。
    if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
      try {
        memberService.spendPoints(order.getUserId(), order.getPointsUsed(),
            order.getOrderNo(), "订单积分抵扣");
        log.info("订单积分抵扣已扣减: orderNo={}, pointsUsed={}", order.getOrderNo(), order.getPointsUsed());
      } catch (Exception e) {
        log.error("[points] 支付成功扣减抵扣积分失败，请人工对账 orderNo={}, pointsUsed={}, reason={}",
            order.getOrderNo(), order.getPointsUsed(), e.getMessage());
      }
    }

    // 任务中心埋点：付款成功即视为完成一单；首单触发"首单完成"；按订单实付金额累加"累计消费"
    try {
      missionService.incrementByKeyword(order.getUserId(), "WEEKLY", "完成 1 单购物", 1);
      missionService.incrementByKeyword(order.getUserId(), "ACHIEVEMENT", "首单完成", 1);
      if (order.getPayAmount() != null) {
        // payAmount 已是美元金额（PayController.createPay 已统一汇率换算），直接按整数累加
        int amountInt = order.getPayAmount().intValue();
        if (amountInt > 0) {
          missionService.accumulateByKeyword(order.getUserId(), "ACHIEVEMENT", "累计消费", amountInt);
        }
      }
    } catch (Exception e) {
      // 任务进度失败不应影响支付主流程
      log.warn("[order] trigger mission failed: orderNo={}, reason={}", orderNo, e.getMessage());
    }

    // 付款确认后实时同步到 WooCommerce
    // syncOrderToWooCommerce 内部已有 try-catch，失败时仅记录 syncStatus=-1，不影响主流程
    try {
      wooCommerceSyncService.syncOrderToWooCommerce(order);
    } catch (Exception e) {
      // 兜底：即便 syncOrderToWooCommerce 内部异常也吞掉，不让支付流程回滚
      log.error("触发 WooCommerce 订单同步时异常: orderNo={}, reason={}",
              orderNo, e.getMessage());
    }

    // 发送订单支付成功通知（异步 + MQ 不可用时降级为同步落库）
    try {
      String orderTitle = "订单支付成功";
      String orderContent = "您的订单 " + order.getOrderNo() + " 已支付成功，商家将尽快为您发货";
      try {
        notificationMessageProducer.send(
                order.getUserId(), "ORDER", orderTitle, orderContent, order.getId());
      } catch (Exception mqEx) {
        // MQ 不可用降级：直接同步落库，保证用户侧能看到通知
        log.warn("MQ 发送失败，降级同步落库通知: orderNo={}, reason={}",
                orderNo, mqEx.getMessage());
        notificationService.saveNotification(
                order.getUserId(), "ORDER", orderTitle, orderContent, order.getId());
      }
    } catch (Exception e) {
      log.warn("[order] send notification failed: orderNo={}, reason={}", orderNo, e.getMessage());
    }

    // 消费返积分：1 USD = 10 积分，首单 2 倍；失败仅打日志不影响支付主流程
    try {
      pointsRewardService.rewardForOrder(order);
    } catch (Exception e) {
      log.warn("[order] grant reward points failed: orderNo={}, reason={}", orderNo, e.getMessage());
    }
  }

  @Override
  @Transactional
  public void confirmReceived(Long orderId, Long userId) {
    OrderEntity order = getOrderDetail(orderId, userId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }
    // 确认收货只能从"已发货"状态流转
    if (!SHIPPED.name().equals(order.getStatus())) {
      throw new IllegalStateException("当前订单状态不允许确认收货");
    }
    order.setStatus(RECEIVED.name());
    order.setReceivedTime(LocalDateTime.now());
    orderMapper.updateById(order);
  }

  @Override
  @Transactional
  public void deleteOrder(Long orderId, Long userId) {
    OrderEntity order = getOrderDetail(orderId, userId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }
    order.setDeleteStatus(1);
    orderMapper.updateById(order);
  }

  @Override
  public List<OrderItemEntity> getOrderItems(Long orderId) {
    return orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
            .eq(OrderItemEntity::getOrderId, orderId));
  }

  @Override
  public Map<Long, List<OrderItemEntity>> getOrderItemsByOrderIds(List<Long> orderIds) {
    if (orderIds == null || orderIds.isEmpty()) {
      return Collections.emptyMap();
    }
    // 单次批量 IN 查询：替代 N+1 调用 getOrderItems(orderId)
    List<OrderItemEntity> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>().in(OrderItemEntity::getOrderId, orderIds));
    if (items == null || items.isEmpty()) {
      return Collections.emptyMap();
    }
    return items.stream().collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
  }

  @Override
  public OrderEntity getOrderByOrderNo(String orderNo) {
    return orderMapper.selectOne(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getOrderNo, orderNo));
  }
}

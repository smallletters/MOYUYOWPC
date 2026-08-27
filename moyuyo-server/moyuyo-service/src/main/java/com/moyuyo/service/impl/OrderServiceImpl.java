package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.order.OrderItemRequest;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.WooCommerceSyncService;
import static com.moyuyo.common.enums.OrderStatusEnum.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
  // 注入 WooCommerce 同步服务：付款回调完成后自动推送订单到 WooCommerce
  private final WooCommerceSyncService wooCommerceSyncService;
  // 任务中心埋点：付款回调后触发"完成 1 单购物 / 首单完成 / 累计消费满 500"
  private final MissionService missionService;

  @Override
  @Transactional
  public OrderEntity createOrder(Long userId, List<OrderItemEntity> items, Long addressId, String remark, String couponId) {
    // 生产防护：禁止空商品列表创建零金额订单
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("订单商品不能为空");
    }
    // 生成订单号: ORD + yyyyMMdd + 8位雪花ID后缀
    String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String snowId = String.valueOf(IdWorker.getId());
    String orderNo = "ORD" + datePart + snowId.substring(snowId.length() - 8);

    // 校验库存并计算商品总金额
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
      // 校验 SKU 是否存在且库存充足，使用原子更新防止并发超卖
      if (item.getSkuId() != null) {
        ProductSkuEntity sku = productSkuMapper.selectById(item.getSkuId());
        if (sku == null) {
          throw new IllegalArgumentException("SKU不存在: " + item.getSkuId());
        }
        // 原子扣减库存：UPDATE mo_product_sku SET stock = stock - qty WHERE id = ? AND stock >= qty
        LambdaUpdateWrapper<ProductSkuEntity> stockWrapper = new LambdaUpdateWrapper<>();
        stockWrapper.eq(ProductSkuEntity::getId, item.getSkuId())
            .setSql("stock = stock - " + item.getQuantity())
            .apply("stock >= {0}", item.getQuantity());
        int affected = productSkuMapper.update(null, stockWrapper);
        if (affected == 0) {
          throw new IllegalStateException("商品库存不足: " + product.getName());
        }
      } else if (product.getStock() != null && product.getStock() < item.getQuantity()) {
        throw new IllegalArgumentException("商品库存不足: " + product.getName() + "，当前库存: " + product.getStock());
      }
      goodsAmount = goodsAmount.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    // 构建订单
    OrderEntity order = new OrderEntity();
    order.setOrderNo(orderNo);
    order.setUserId(userId);
    order.setGoodsAmount(goodsAmount);
    order.setFreight(BigDecimal.ZERO);
    order.setPayAmount(goodsAmount);
    order.setStatus(PENDING_PAY.name());
    order.setAddressId(addressId);
    order.setRemark(remark);
    order.setCouponId(couponId);
    order.setDeleteStatus(0);
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
    List<OrderItemEntity> items = new ArrayList<>();
    for (OrderItemRequest itemReq : request.getItems()) {
      ProductSkuEntity sku = productSkuMapper.selectById(itemReq.getSkuId());
      if (sku == null) {
        throw new IllegalArgumentException("SKU不存在: " + itemReq.getSkuId());
      }
      ProductEntity product = productMapper.selectById(itemReq.getProductId());
      if (product == null) {
        throw new IllegalArgumentException("商品不存在: " + itemReq.getProductId());
      }

      OrderItemEntity item = new OrderItemEntity();
      item.setSkuId(itemReq.getSkuId());
      item.setProductId(itemReq.getProductId());
      item.setProductName(product.getName());
      item.setMainImage(product.getMainImage());
      item.setPrice(sku.getPrice());
      item.setQuantity(itemReq.getQuantity());
      items.add(item);
    }

    return createOrder(userId, items, request.getAddressId(), request.getRemark(), request.getCouponId());
  }

  @Override
  public IPage<OrderEntity> listOrders(Long userId, int page, int size, String status) {
    LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getDeleteStatus, 0);
    // 管理员(userId=null)查看全部订单，不限制userId
    if (userId != null) {
        wrapper.eq(OrderEntity::getUserId, userId);
    }

    // 按状态筛选
    if (status != null && !status.isEmpty()) {
      wrapper.eq(OrderEntity::getStatus, status);
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
    return order;
  }

  @Override
  @Transactional
  public void cancelOrder(Long orderId, Long userId, String reason) {
    OrderEntity order = getOrderDetail(orderId, userId);
    if (order == null) {
      throw new IllegalArgumentException("订单不存在: " + orderId);
    }
    if (!PENDING_PAY.name().equals(order.getStatus())) {
      throw new IllegalStateException("当前订单状态不允许取消");
    }

    // P1 修复：取消订单时恢复已扣减的库存，避免 SKU 库存永久减少
    // 原实现仅更新订单状态，createOrder 中扣减的 stock 不会被回滚，
    // 导致用户取消订单后该 SKU 库存"看起来永久减少"（实际库存已经被买走了，但订单未付款）
    // 注：这里采用"加法恢复"而非乐观锁校验，避免与订单超时取消任务（OrderTimeoutConsumer）产生死锁竞争
    List<OrderItemEntity> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
            .eq(OrderItemEntity::getOrderId, orderId));
    if (items != null) {
      for (OrderItemEntity item : items) {
        if (item.getSkuId() != null && item.getQuantity() != null && item.getQuantity() > 0) {
          // 原子累加：UPDATE mo_product_sku SET stock = stock + qty WHERE id = ?
          // 不带 WHERE 库存上下限约束：允许临时超过上限（与创建订单并发场景下，安全优先）
          LambdaUpdateWrapper<ProductSkuEntity> restoreWrapper = new LambdaUpdateWrapper<>();
          restoreWrapper.eq(ProductSkuEntity::getId, item.getSkuId())
              .setSql("stock = stock + " + item.getQuantity());
          int affected = productSkuMapper.update(null, restoreWrapper);
          if (affected == 0) {
            // 极端情况：SKU 已被删除
            log.warn("取消订单恢复库存失败：SKU不存在或已删除，skuId={}, orderId={}",
                    item.getSkuId(), orderId);
          }
        }
      }
    }

    order.setStatus(CANCELLED.name());
    order.setCancelTime(LocalDateTime.now());
    order.setCancelReason(reason);
    orderMapper.updateById(order);
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
    if (!PENDING_PAY.name().equals(order.getStatus())) {
      log.warn("支付回调重复处理: orderNo={}, status={}", orderNo, order.getStatus());
      return;
    }

    // 更新订单状态
    order.setStatus(PAID.name());
    order.setPaidAt(LocalDateTime.now());
    order.setPayChannel(payChannel);
    order.setPayTransactionId(transactionId);
    orderMapper.updateById(order);

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

package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.moyuyo.service.OrderService;
import static com.moyuyo.common.enums.OrderStatusEnum.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final PaymentMapper paymentMapper;
  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;

  @Override
  @Transactional
  public OrderEntity createOrder(Long userId, List<OrderItemEntity> items, Long addressId, String remark, String couponId) {
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
      // 校验 SKU 是否存在且库存充足
      if (item.getSkuId() != null) {
        ProductSkuEntity sku = productSkuMapper.selectById(item.getSkuId());
        if (sku == null) {
          throw new IllegalArgumentException("SKU不存在: " + item.getSkuId());
        }
        if (sku.getStock() != null && sku.getStock() < item.getQuantity()) {
          throw new IllegalArgumentException("商品库存不足: " + product.getName() + "，当前库存: " + sku.getStock());
        }
        // 扣减 SKU 库存
        sku.setStock(sku.getStock() - item.getQuantity());
        productSkuMapper.updateById(sku);
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
      throw new IllegalArgumentException("订单不存在");
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
    if (!PENDING_PAY.name().equals(order.getStatus())) {
      throw new IllegalStateException("当前订单状态不允许取消");
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
  }

  @Override
  @Transactional
  public void confirmReceived(Long orderId, Long userId) {
    OrderEntity order = getOrderDetail(orderId, userId);
    if (!PAID.name().equals(order.getStatus())) {
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
  public OrderEntity getOrderByOrderNo(String orderNo) {
    return orderMapper.selectOne(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getOrderNo, orderNo));
  }
}

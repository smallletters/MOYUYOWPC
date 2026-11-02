package com.moyuyo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客服聊天上下文构建服务。
 *
 * 统一负责「咨询商品 / 咨询订单」以及聊天卡片的 JSON 快照生成，
 * 后台推荐商品、发送订单卡片、APP 端渲染卡片都复用这里的结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
// 抑制 JDT 对 MyBatis-Plus Lambda 方法引用的 @Nonnull 空值分析误报
@SuppressWarnings("null")
public class CsChatContextService {

  private final ProductMapper productMapper;
  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final ObjectMapper objectMapper;

  /**
   * 商品快照：商品不存在时返回 null。
   * 包含卡片渲染需要的图片、价格、上下架状态。
   */
  public Map<String, Object> productSnapshot(Long productId) {
    if (productId == null) return null;
    ProductEntity product = productMapper.selectById(productId);
    if (product == null) return null;
    return buildProductMap(product);
  }

  /**
   * 商品快照 + 进入会话时携带的名称/图片兜底。
   * 商品已删除或临时无法查询时，仍能展示用户当时咨询的商品名称与图片。
   */
  public Map<String, Object> productSnapshotWithFallback(Long productId, String fallbackName, String fallbackImage) {
    Map<String, Object> snapshot = productSnapshot(productId);
    if (snapshot != null) {
      if ((snapshot.get("name") == null || String.valueOf(snapshot.get("name")).isBlank()) && fallbackName != null) {
        snapshot.put("name", fallbackName);
      }
      if (snapshot.get("image") == null && fallbackImage != null) {
        snapshot.put("image", fallbackImage);
      }
      return snapshot;
    }
    if (productId == null && (fallbackName == null || fallbackName.isBlank())) return null;
    // 商品已删除：用进入会话时缓存的名称/图片兜底
    Map<String, Object> fallback = new LinkedHashMap<>();
    fallback.put("id", productId);
    fallback.put("name", fallbackName == null ? "" : fallbackName);
    fallback.put("image", fallbackImage);
    return fallback;
  }

  /**
   * 订单快照：订单不存在或不属于指定用户时返回 null。
   * userId 传 null 表示不校验归属（后台查看场景）。
   */
  public Map<String, Object> orderSnapshot(Long orderId, Long userId) {
    if (orderId == null) return null;
    OrderEntity order = orderMapper.selectById(orderId);
    if (order == null) return null;
    if (userId != null && !userId.equals(order.getUserId())) return null;
    return buildOrderMap(order);
  }

  /**
   * 按订单号 + 用户查询订单快照（会话上下文使用）。
   */
  public Map<String, Object> orderSnapshotByNo(String orderNo, Long userId) {
    if (orderNo == null || orderNo.isBlank()) return null;
    LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getOrderNo, orderNo)
        .and(w -> w.isNull(OrderEntity::getDeleteStatus).or().eq(OrderEntity::getDeleteStatus, 0))
        .orderByDesc(OrderEntity::getCreateTime)
        .last("LIMIT 1");
    if (userId != null) wrapper.eq(OrderEntity::getUserId, userId);
    OrderEntity order = orderMapper.selectOne(wrapper);
    return order == null ? null : buildOrderMap(order);
  }

  /**
   * 用户最近订单列表（后台客服侧栏展示，可直接发送订单卡片给用户）。
   */
  public List<Map<String, Object>> listUserOrders(Long userId, int limit) {
    if (userId == null) return List.of();
    int safeLimit = Math.max(1, Math.min(limit, 50));
    List<OrderEntity> orders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, userId)
            .and(w -> w.isNull(OrderEntity::getDeleteStatus).or().eq(OrderEntity::getDeleteStatus, 0))
            .orderByDesc(OrderEntity::getCreateTime)
            .last("LIMIT " + safeLimit)
    );
    return orders.stream().map(this::buildOrderMap).collect(Collectors.toList());
  }

  /** 对象转 JSON；失败时返回 null，避免卡片序列化异常影响消息发送 */
  public String toJson(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      log.warn("客服卡片快照序列化失败", e);
      return null;
    }
  }

  private Map<String, Object> buildProductMap(ProductEntity product) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", product.getId());
    map.put("name", product.getName());
    map.put("image", product.getMainImage());
    map.put("price", product.getPrice());
    map.put("originalPrice", product.getOriginalPrice());
    map.put("spuCode", product.getSpuCode());
    map.put("onSale", product.getOnSale());
    map.put("stockStatus", product.getStockStatus());
    return map;
  }

  private Map<String, Object> buildOrderMap(OrderEntity order) {
    List<OrderItemEntity> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
            .eq(OrderItemEntity::getOrderId, order.getId())
    );
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", order.getId());
    map.put("orderNo", order.getOrderNo());
    map.put("status", order.getStatus());
    map.put("payAmount", order.getPayAmount());
    map.put("currency", order.getCurrency());
    map.put("createTime", order.getCreateTime());
    map.put("items", items.stream().map(item -> {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("productId", item.getProductId());
      row.put("name", item.getProductName());
      row.put("image", item.getMainImage());
      row.put("skuSpec", item.getSkuSpec());
      row.put("price", item.getPrice());
      row.put("quantity", item.getQuantity());
      row.put("subtotal", item.getSubtotal());
      return row;
    }).collect(Collectors.toList()));
    return map;
  }
}

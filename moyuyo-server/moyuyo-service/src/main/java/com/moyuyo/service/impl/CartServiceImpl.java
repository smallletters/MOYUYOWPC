package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.CartMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.CartService;
import com.moyuyo.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

  private final CartMapper cartMapper;
  private final ProductSkuMapper productSkuMapper;
  private final ProductMapper productMapper;
  private final OrderService orderService;

  @Override
  public List<CartEntity> getUserCart(Long userId) {
    List<CartEntity> carts = cartMapper.selectList(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId));
    // 联查当前可用库存与商品可用状态，随购物车列表返回给前端：
    // - stock 用于限制加购数量上限
    // - cartStatus 用于购物车页分区展示（可售/缺货/已下架/失效）
    fillAvailability(carts);
    return carts;
  }

  @Override
  @Transactional
  public CartEntity addItem(Long userId, Long skuId, int quantity) {
    CartEntity existing = cartMapper.selectOne(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSkuId, skuId));

    if (existing != null) {
      // 已在购物车：先确认商品仍有效(未删除/未下架)再合并数量
      ProductEntity existProduct = existing.getProductId() == null
          ? null : productMapper.selectById(existing.getProductId());
      if (existProduct == null || !Boolean.TRUE.equals(existProduct.getOnSale())) {
        throw new IllegalArgumentException("商品已失效或已下架,无法再次加入购物车: " + existing.getProductName());
      }
      int target = existing.getQuantity() + quantity;
      checkQuantityWithinStock(existing.getProductName(), skuId, target);
      existing.setQuantity(target);
      cartMapper.updateById(existing);
      return existing;
    }

    // 新加购：回查 SKU/商品并写入快照列，保证服务端购物车列表可独立渲染（名称/主图/单价）
    ProductSkuEntity sku = productSkuMapper.selectById(skuId);
    if (sku == null) {
      throw new IllegalArgumentException("商品SKU不存在: " + skuId);
    }
    ProductEntity product = sku.getProductId() == null ? null : productMapper.selectById(sku.getProductId());
    // 下架/已删除商品不允许加入购物车(商品列表已过滤,此处兜底直接访问的场景)
    if (product == null) {
      throw new IllegalArgumentException("商品不存在或已删除: " + skuId);
    }
    if (!Boolean.TRUE.equals(product.getOnSale())) {
      throw new IllegalArgumentException("商品已下架,无法加入购物车: " + product.getName());
    }
    // 校验加购数量是否超过库存
    checkQuantityWithinStock(product.getName(), skuId, quantity);

    CartEntity cart = new CartEntity();
    cart.setUserId(userId);
    cart.setSkuId(skuId);
    cart.setProductId(sku.getProductId());
    cart.setProductName(product == null ? null : product.getName());
    cart.setMainImage(product == null ? null : product.getMainImage());
    cart.setPrice(sku.getPrice());
    cart.setQuantity(quantity);
    cart.setSelected(true);
    cartMapper.insert(cart);
    return cart;
  }

  @Override
  @Transactional
  public CartEntity updateQuantity(Long userId, Long skuId, int quantity) {
    CartEntity existing = cartMapper.selectOne(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSkuId, skuId));

    if (existing == null) {
      throw new IllegalArgumentException("购物车中未找到该商品");
    }

    // 与失效区/缺货设计对齐：已下架、商品已删除、规格已删除的下单项不允许再改数量（可走删除接口移除）
    ProductEntity product = existing.getProductId() == null
        ? null : productMapper.selectById(existing.getProductId());
    if (product == null) {
      throw new IllegalArgumentException("商品已失效: " + existing.getProductName());
    }
    if (!Boolean.TRUE.equals(product.getOnSale())) {
      throw new IllegalArgumentException("商品已下架: " + product.getName());
    }
    // skuId 恰好等于商品 id 表示无独立 SKU 的简单商品（跳过 SKU 存在性检查）
    if (existing.getSkuId() != null && !existing.getSkuId().equals(existing.getProductId())
        && productSkuMapper.selectById(existing.getSkuId()) == null) {
      throw new IllegalArgumentException("该规格已失效,请从购物车移除后重新选择");
    }

    // 改数量同样受库存上限约束
    checkQuantityWithinStock(existing.getProductName(), skuId, quantity);
    existing.setQuantity(quantity);
    cartMapper.updateById(existing);
    return existing;
  }

  @Override
  @Transactional
  public void removeItem(Long userId, Long skuId) {
    cartMapper.delete(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSkuId, skuId));
  }

  @Override
  @Transactional
  public void toggleCheck(Long userId, Long skuId, Boolean selected) {
    CartEntity existing = cartMapper.selectOne(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSkuId, skuId));

    if (existing == null) {
      throw new IllegalArgumentException("购物车中未找到该商品");
    }

    existing.setSelected(selected);
    cartMapper.updateById(existing);
  }

  @Override
  @Transactional
  public void toggleCheckAll(Long userId, boolean selected) {
    CartEntity update = new CartEntity();
    update.setSelected(selected);
    cartMapper.update(update,
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId));
  }

  @Override
  @Transactional
  public void clear(Long userId) {
    cartMapper.delete(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId));
  }

  @Override
  @Transactional
  public OrderEntity checkout(Long userId, Long addressId, String remark, String couponId) {
    List<CartEntity> selected = cartMapper.selectList(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSelected, true));

    if (selected.isEmpty()) {
      throw new IllegalArgumentException("请选择要结算的商品");
    }

    List<OrderItemEntity> items = new java.util.ArrayList<>();
    for (CartEntity cart : selected) {
      ProductSkuEntity sku = productSkuMapper.selectById(cart.getSkuId());
      if (sku == null) {
        throw new IllegalArgumentException("商品SKU不存在: " + cart.getSkuId());
      }
      ProductEntity product = productMapper.selectById(sku.getProductId());
      if (product == null) {
        throw new IllegalArgumentException("商品不存在: " + sku.getProductId());
      }

      OrderItemEntity item = new OrderItemEntity();
      item.setSkuId(cart.getSkuId());
      item.setProductId(product.getId());
      item.setProductName(product.getName());
      item.setMainImage(product.getMainImage());
      item.setPrice(sku.getPrice());
      item.setQuantity(cart.getQuantity());
      items.add(item);
    }

    OrderEntity order = orderService.createOrder(userId, items, addressId, remark, couponId);

    cartMapper.delete(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSelected, true));

    log.info("Cart checkout: userId={}, orderNo={}, items={}", userId, order.getOrderNo(), items.size());
    return order;
  }

  /**
   * 批量计算购物车条目可用状态与库存
   * <p>
   * 状态判定顺序（模拟主流电商购物车）：
   * - 商品行不存在            → PRODUCT_GONE（商品已删除）
   * - 商品已下架(onSale=false) → OFF_SALE（移入失效区，不可结算）
   * - SKU 行存在              → 按 SKU 库存判 VALID / OUT_OF_STOCK
   * - skuId 恰等于商品 id      → 兼容简单商品，按商品主库存判 VALID / OUT_OF_STOCK
   * - SKU 找不到              → INVALID_SKU（规格已失效，移入失效区）
   */
  private void fillAvailability(List<CartEntity> carts) {
    if (carts == null || carts.isEmpty()) {
      return;
    }
    List<Long> skuIds = carts.stream()
        .map(CartEntity::getSkuId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    List<Long> productIds = carts.stream()
        .map(CartEntity::getProductId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    Map<Long, ProductSkuEntity> skuMap = skuIds.isEmpty() ? Collections.emptyMap()
        : productSkuMapper.selectBatchIds(skuIds).stream()
            .collect(Collectors.toMap(ProductSkuEntity::getId, s -> s, (a, b) -> a));
    Map<Long, ProductEntity> productMap = productIds.isEmpty() ? Collections.emptyMap()
        : productMapper.selectBatchIds(productIds).stream()
            .collect(Collectors.toMap(ProductEntity::getId, p -> p, (a, b) -> a));

    for (CartEntity cart : carts) {
      ProductEntity product = cart.getProductId() == null ? null : productMap.get(cart.getProductId());
      ProductSkuEntity sku = cart.getSkuId() == null ? null : skuMap.get(cart.getSkuId());

      String status;
      Integer stock = null;
      if (product == null) {
        status = "PRODUCT_GONE";
      } else if (!Boolean.TRUE.equals(product.getOnSale())) {
        status = "OFF_SALE";
      } else if (sku != null) {
        stock = sku.getStock();
        status = stock != null && stock <= 0 ? "OUT_OF_STOCK" : "VALID";
      } else if (cart.getSkuId() != null && cart.getSkuId().equals(cart.getProductId())) {
        // 兼容 skuId=商品 id 的简单商品（无独立 SKU 行）
        stock = product.getStock();
        status = stock != null && stock <= 0 ? "OUT_OF_STOCK" : "VALID";
      } else if (cart.getSkuId() != null) {
        status = "INVALID_SKU";
      } else {
        stock = product.getStock();
        status = stock != null && stock <= 0 ? "OUT_OF_STOCK" : "VALID";
      }
      cart.setStock(stock);
      cart.setCartStatus(status);
    }
  }

  /**
   * 校验加购/改数量是否超过库存上限，超过则抛业务异常
   */
  private void checkQuantityWithinStock(String productName, Long skuId, int quantity) {
    if (quantity < 1) {
      throw new IllegalArgumentException("商品数量必须大于0");
    }
    int available = resolveAvailableStock(skuId);
    if (available != Integer.MAX_VALUE && quantity > available) {
      String label = productName == null || productName.isBlank() ? String.valueOf(skuId) : productName;
      throw new IllegalArgumentException("商品库存不足，当前仅剩 " + available + " 件: " + label);
    }
  }

  /**
   * 解析可用库存：优先 SKU.stock；SKU 无库存字段时降级商品库存；
   * skuId 本身可能就是简单商品 id（SKU 不存在）时按商品查询；
   * 均查不到时返回不限制，最终以下单时原子扣减为准
   */
  private int resolveAvailableStock(Long skuId) {
    if (skuId != null) {
      ProductSkuEntity sku = productSkuMapper.selectById(skuId);
      if (sku != null) {
        if (sku.getStock() != null) {
          return sku.getStock();
        }
        if (sku.getProductId() != null) {
          ProductEntity product = productMapper.selectById(sku.getProductId());
          if (product != null && product.getStock() != null) {
            return product.getStock();
          }
        }
      } else {
        // SKU 查不到：降级按商品主库存（兼容 skuId=商品 id 的简单商品）
        ProductEntity product = productMapper.selectById(skuId);
        if (product != null && product.getStock() != null) {
          return product.getStock();
        }
      }
    }
    return Integer.MAX_VALUE;
  }
}

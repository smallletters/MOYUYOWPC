package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.dao.mapper.CartMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.CartService;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.admin.ShippingZoneService;
import com.moyuyo.service.support.ProductAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

  private final CartMapper cartMapper;
  private final ProductSkuMapper productSkuMapper;
  private final ProductMapper productMapper;
  private final AddressMapper addressMapper;
  private final ShippingZoneService shippingZoneService;
  private final OrderService orderService;
  private final CampaignPricingServiceImpl campaignPricingService;

  @Override
  @SuppressWarnings("null")
  public List<CartEntity> getUserCart(Long userId) {
    List<CartEntity> carts = cartMapper.selectList(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId));
    // 联查当前可用库存与商品可用状态，随购物车列表返回给前端：
    // - stock 用于限制加购数量上限
    // - cartStatus 用于购物车页分区展示（可售/缺货/已下架/失效）
    fillAvailability(carts);
    // 填充营销活动价（活动商品不参与会员价，规则与下单保持一致）
    campaignPricingService.fillCartItems(carts, userId);
    return carts;
  }

  @Override
  @Transactional
  @SuppressWarnings("null")
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
      // 单独出售商品：合并后也不能超过 1 件
      ensureSellableQuantity(existProduct, target);
      checkQuantityWithinStock(existProduct, skuId, target);
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
    // 单独出售商品：只能购买 1 件
    ensureSellableQuantity(product, quantity);
    // 校验加购数量是否超过库存（允许缺货订购时不拦截）
    checkQuantityWithinStock(product, skuId, quantity);

    CartEntity cart = new CartEntity();
    cart.setUserId(userId);
    cart.setSkuId(skuId);
    cart.setProductId(sku.getProductId());
    cart.setProductName(product == null ? null : product.getName());
    cart.setMainImage(product == null ? null : product.getMainImage());
    // 加购快照价同样按销售期口径计算
    cart.setPrice(ProductAttributes.effectivePrice(
        ProductAttributes.parse(product.getAttributes()),
        sku.getSkuCode(), sku.getSpec(), sku.getPrice(), product.getOriginalPrice()));
    cart.setQuantity(quantity);
    cart.setSelected(true);
    cartMapper.insert(cart);
    return cart;
  }

  @Override
  @Transactional
  @SuppressWarnings("null")
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

    // 改数量同样受「单独出售」与库存上限约束
    ensureSellableQuantity(product, quantity);
    checkQuantityWithinStock(product, skuId, quantity);
    existing.setQuantity(quantity);
    cartMapper.updateById(existing);
    return existing;
  }

  @Override
  @Transactional
  @SuppressWarnings("null")
  public void removeItem(Long userId, Long skuId) {
    cartMapper.delete(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSkuId, skuId));
  }

  @Override
  @Transactional
  @SuppressWarnings("null")
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
  @SuppressWarnings("null")
  public void toggleCheckAll(Long userId, boolean selected) {
    CartEntity update = new CartEntity();
    update.setSelected(selected);
    cartMapper.update(update,
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId));
  }

  @Override
  @Transactional
  @SuppressWarnings("null")
  public void clear(Long userId) {
    cartMapper.delete(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId));
  }

  @Override
  @Transactional
  @SuppressWarnings("null")
  public OrderEntity checkout(Long userId, Long addressId, String remark, String couponId) {
    List<CartEntity> selected = cartMapper.selectList(
        new LambdaQueryWrapper<CartEntity>()
            .eq(CartEntity::getUserId, userId)
            .eq(CartEntity::getSelected, true));

    if (selected.isEmpty()) {
      throw new IllegalArgumentException("请选择要结算的商品");
    }

    // 先构建订单项并判断是否全部为虚拟商品：虚拟订单无需收货地址与运费
    List<OrderItemEntity> items = new java.util.ArrayList<>();
    boolean allVirtual = true;
    for (CartEntity cart : selected) {
      ProductSkuEntity sku = productSkuMapper.selectById(cart.getSkuId());
      if (sku == null) {
        throw new IllegalArgumentException("商品SKU不存在: " + cart.getSkuId());
      }
      ProductEntity product = productMapper.selectById(sku.getProductId());
      if (product == null) {
        throw new IllegalArgumentException("商品不存在: " + sku.getProductId());
      }
      Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
      if (!ProductAttributes.bool(attrs, "is_virtual", false)) {
        allVirtual = false;
      }

      OrderItemEntity item = new OrderItemEntity();
      item.setSkuId(cart.getSkuId());
      item.setProductId(product.getId());
      item.setProductName(product.getName());
      item.setMainImage(product.getMainImage());
      // 服务端权威价：销售期外回退常规价，避免过期促销价被结算
      item.setPrice(ProductAttributes.effectivePrice(
          attrs, sku.getSkuCode(), sku.getSpec(), sku.getPrice(), product.getOriginalPrice()));
      item.setQuantity(cart.getQuantity());
      items.add(item);
    }

    // 兜底：实物订单地址可发货校验。运营通过后台"发货区域"控制可发货国家；
    // 即便 APP 端未及时校验或被绕过，下单时再拒绝一次。虚拟订单跳过。
    if (!allVirtual) {
      AddressEntity address = addressMapper.selectById(addressId);
      if (address == null) {
        throw new IllegalArgumentException("收货地址不存在");
      }
      String country = address.getCountry();
      if (country == null || country.isBlank()) {
        throw new IllegalArgumentException("收货地址国家不能为空");
      }
      if (!shippingZoneService.loadShippableCountries().contains(country.trim().toUpperCase())) {
        log.warn("Cart checkout rejected (unshippable country): userId={}, addressId={}, country={}",
            userId, addressId, country);
        throw new BusinessException(400, "We currently do not ship to " + country);
      }
    }

    OrderEntity order = orderService.createOrder(userId, items, allVirtual ? null : addressId, remark, couponId);

    // 关键：购物车清空失败不能让整个事务回滚（订单已经成功下发，可能已扣库存）
    // 失败仅记日志，由用户在下次进入购物车时手动清空；不影响订单本身。
    try {
      cartMapper.delete(
          new LambdaQueryWrapper<CartEntity>()
              .eq(CartEntity::getUserId, userId)
              .eq(CartEntity::getSelected, true));
    } catch (Exception e) {
      log.error("Cart checkout: clear cart failed (order still valid). userId={}, orderNo={}",
          userId, order.getOrderNo(), e);
    }

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
  @SuppressWarnings("null")
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
    // 使用 selectList + in 取代过时的 selectBatchIds，同时规避其 @Nullable 返回值带来的 null type safety 警告
    Map<Long, ProductSkuEntity> skuMap = skuIds.isEmpty() ? Collections.emptyMap()
        : productSkuMapper.selectList(
            new LambdaQueryWrapper<ProductSkuEntity>().in(ProductSkuEntity::getId, skuIds)).stream()
            .collect(Collectors.toMap(ProductSkuEntity::getId, s -> s, (a, b) -> a));
    Map<Long, ProductEntity> productMap = productIds.isEmpty() ? Collections.emptyMap()
        : productMapper.selectList(
            new LambdaQueryWrapper<ProductEntity>().in(ProductEntity::getId, productIds)).stream()
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
      // 允许缺货订购的商品：库存为 0 仍可结算，并告知 APP 放宽数量上限
      Map<String, Object> attrs = ProductAttributes.parse(product == null ? null : product.getAttributes());
      boolean allowBackorder = product != null
          && !"no".equalsIgnoreCase(ProductAttributes.str(attrs, "backorders", "no"));
      if ("OUT_OF_STOCK".equals(status) && allowBackorder) {
        status = "VALID";
      }
      cart.setStock(stock);
      cart.setCartStatus(status);
      cart.setAllowBackorder(allowBackorder);
      cart.setIsVirtual(product != null && ProductAttributes.bool(attrs, "is_virtual", false));
      cart.setTaxStatus(ProductAttributes.str(attrs, "tax_status", "taxable"));
      cart.setTaxClass(ProductAttributes.str(attrs, "tax_class", "standard"));
      // 积分抵扣开关：APP 结算页据此决定是否展示积分抵扣
      cart.setAllowPointsDeduct(product == null || !Boolean.FALSE.equals(product.getAllowPointsDeduct()));
      if (product != null) {
        // 销售期外：购物车展示价回退到常规价（与商品详情口径一致）
        cart.setPrice(ProductAttributes.effectivePrice(
            attrs,
            sku != null ? sku.getSkuCode() : null,
            sku != null ? sku.getSpec() : null,
            cart.getPrice(),
            product.getOriginalPrice()));
      }
    }
  }

  /**
   * 单独出售（sold_individually）校验：每个订单最多购买 1 件。
   */
  private void ensureSellableQuantity(ProductEntity product, int quantity) {
    if (quantity < 1) {
      throw new IllegalArgumentException("商品数量必须大于0");
    }
    Map<String, Object> attrs = ProductAttributes.parse(product == null ? null : product.getAttributes());
    if (ProductAttributes.bool(attrs, "sold_individually", false) && quantity > 1) {
      String label = product == null || product.getName() == null ? "" : ": " + product.getName();
      throw new IllegalArgumentException("该商品每单限购 1 件" + label);
    }
  }

  /** 是否允许缺货订购（backorders = notify / yes） */
  private boolean allowsBackorder(ProductEntity product) {
    if (product == null) {
      return false;
    }
    Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
    return !"no".equalsIgnoreCase(ProductAttributes.str(attrs, "backorders", "no"));
  }

  /**
   * 校验加购/改数量是否超过库存上限，超过则抛业务异常。
   * 允许缺货订购时跳过库存上限校验（下单环节同样放开扣减）。
   */
  private void checkQuantityWithinStock(ProductEntity product, Long skuId, int quantity) {
    if (quantity < 1) {
      throw new IllegalArgumentException("商品数量必须大于0");
    }
    if (allowsBackorder(product)) {
      return;
    }
    int available = resolveAvailableStock(skuId);
    if (available != Integer.MAX_VALUE && quantity > available) {
      String productName = product == null ? null : product.getName();
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

  /**
   * 购物车凑单推荐：读取购物车商品 attributes 中的 upsell_ids / cross_sell_ids，
   * 返回仍在上架、且不在当前购物车中的商品（最多 8 个）。
   */
  @Override
  public List<ProductEntity> getRecommendations(Long userId) {
    List<CartEntity> carts = cartMapper.selectList(
        new LambdaQueryWrapper<CartEntity>().eq(CartEntity::getUserId, userId));
    if (carts.isEmpty()) {
      return Collections.emptyList();
    }
    Set<Long> cartProductIds = carts.stream()
        .map(CartEntity::getProductId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (cartProductIds.isEmpty()) {
      return Collections.emptyList();
    }
    // 收集购物车商品的关联商品 id（保持配置顺序，先 upsell 后 cross-sell）
    LinkedHashSet<Long> referenceIds = new LinkedHashSet<>();
    for (ProductEntity product : productMapper.selectBatchIds(cartProductIds)) {
      Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
      referenceIds.addAll(ProductAttributes.longList(attrs, "upsell_ids"));
      referenceIds.addAll(ProductAttributes.longList(attrs, "cross_sell_ids"));
    }
    referenceIds.removeAll(cartProductIds);
    if (referenceIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<ProductEntity> candidates = productMapper.selectBatchIds(referenceIds.stream().limit(20).toList());
    List<ProductEntity> result = new java.util.ArrayList<>();
    for (ProductEntity candidate : candidates) {
      if (!Boolean.TRUE.equals(candidate.getOnSale())) {
        continue;
      }
      // 与商品详情/购物车保持同一价格口径（销售期外回退常规价）
      Map<String, Object> attrs = ProductAttributes.parse(candidate.getAttributes());
      candidate.setPrice(ProductAttributes.effectivePrice(
          attrs, null, null, candidate.getPrice(), candidate.getOriginalPrice()));
      result.add(candidate);
      if (result.size() >= 8) {
        break;
      }
    }
    return result;
  }
}

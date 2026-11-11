package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.admin.entity.CampaignUsageEntity;
import com.moyuyo.dao.admin.entity.CampaignUserUsageEntity;
import com.moyuyo.dao.admin.entity.MarketingCampaignEntity;
import com.moyuyo.dao.admin.mapper.CampaignUsageMapper;
import com.moyuyo.dao.admin.mapper.CampaignUserUsageMapper;
import com.moyuyo.dao.admin.mapper.MarketingCampaignMapper;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 营销活动计价服务。
 *
 * <p>支持：百分比折扣、直降、满减；秒杀与拼团不参与。
 * <p>叠加规则：活动价替代商品售价，活动商品不参与会员价；优惠券/积分在活动后金额上继续抵扣。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CampaignPricingServiceImpl {

  private static final String SCOPE_ALL = "全部商品";
  private static final String SCOPE_PRODUCT = "指定商品";
  private static final String SCOPE_CATEGORY = "指定品类";

  private final MarketingCampaignMapper campaignMapper;
  private final CampaignUsageMapper usageMapper;
  private final CampaignUserUsageMapper userUsageMapper;
  private final ProductMapper productMapper;
  private final FlashSaleMapper flashSaleMapper;

  /**
   * 单商品计价结果（供商品列表/详情/前端展示）。
   */
  public static class ItemPrice {
    public MarketingCampaignEntity campaign;
    public String campaignType;
    public BigDecimal campaignValue;
    public BigDecimal minOrderAmount;
    public BigDecimal originalPrice;
    public BigDecimal finalPrice;
    /** 单件优惠金额 */
    public BigDecimal discountPerUnit;
    /** 该行总优惠（= 单件优惠 × 数量） */
    public BigDecimal lineDiscount;
    public int quantity;
    public String tag;
  }

  /**
   * 整单计价结果。
   */
  public static class OrderPricing {
    /** 满减等整单优惠 */
    public BigDecimal orderDiscount = BigDecimal.ZERO;
    /** 活动优惠总额（活动价优惠 + 满减） */
    public BigDecimal totalDiscount = BigDecimal.ZERO;
    public List<Reservation> reservations = new ArrayList<>();
  }

  /**
   * 下单时需要占用的一条活动额度。
   */
  public static class Reservation {
    public Long campaignId;
    public int itemCount;
    public BigDecimal discountAmount;
  }

  /**
   * 一次计价上下文：活动列表 + 当前用户已占用件数，避免列表 N+1 查询。
   */
  public static class Context {
    List<MarketingCampaignEntity> campaigns;
    Map<Long, Integer> userUsed = new HashMap<>();
    /** 当前秒杀商品集合：秒杀与营销活动不叠加 */
    Set<Long> flashSaleProductIds = new HashSet<>();
  }

  /** 构建计价上下文 */
  public Context buildContext(Long userId) {
    Context ctx = new Context();
    LocalDateTime now = LocalDateTime.now();
    ctx.campaigns = campaignMapper.selectList(
        new LambdaQueryWrapper<MarketingCampaignEntity>()
            .le(MarketingCampaignEntity::getStartDate, now)
            .ge(MarketingCampaignEntity::getEndDate, now)
            .notIn(MarketingCampaignEntity::getStatus, "DRAFT", "ENDED")
            .orderByAsc(MarketingCampaignEntity::getId));
    if (userId != null && !ctx.campaigns.isEmpty()) {
      List<Long> ids = ctx.campaigns.stream().map(MarketingCampaignEntity::getId).toList();
      List<CampaignUserUsageEntity> usages = userUsageMapper.selectList(
          new LambdaQueryWrapper<CampaignUserUsageEntity>()
              .eq(CampaignUserUsageEntity::getUserId, userId)
              .in(CampaignUserUsageEntity::getCampaignId, ids));
      for (CampaignUserUsageEntity u : usages) {
        ctx.userUsed.put(u.getCampaignId(), u.getUsedCount() == null ? 0 : u.getUsedCount());
      }
    }
    // 秒杀商品不参与营销活动
    List<FlashSaleEntity> flashSales = flashSaleMapper.selectList(
        new LambdaQueryWrapper<FlashSaleEntity>()
            .eq(FlashSaleEntity::getActive, true)
            .le(FlashSaleEntity::getStartTime, now)
            .ge(FlashSaleEntity::getEndTime, now));
    for (FlashSaleEntity f : flashSales) {
      if (f.getProductId() != null) ctx.flashSaleProductIds.add(f.getProductId());
    }
    return ctx;
  }

  /** 为商品列表/详情填充活动展示字段（取对用户最优惠的商品级活动） */
  public void fillProductCampaign(ProductEntity product, Long userId) {
    fillProductCampaign(product, userId, buildContext(userId));
  }

  /** 批量填充商品（复用同一个上下文，避免 N 次查询） */
  public void fillProductCampaigns(List<ProductEntity> products, Long userId) {
    if (products == null || products.isEmpty()) return;
    Context ctx = buildContext(userId);
    for (ProductEntity product : products) {
      fillProductCampaign(product, userId, ctx);
    }
  }

  private void fillProductCampaign(ProductEntity product, Long userId, Context ctx) {
    if (product == null || product.getPrice() == null) return;
    ItemPrice best = priceItem(product, product.getPrice(), 1, userId, ctx);
    if (best == null || best.campaign == null) return;
    product.setCampaignId(best.campaign.getId());
    product.setCampaignName(best.campaign.getName());
    product.setCampaignType(best.campaignType);
    product.setCampaignValue(best.campaignValue);
    product.setCampaignMinAmount(best.minOrderAmount);
    product.setCampaignTag(best.tag);
    product.setCampaignPrice(best.finalPrice);
    product.setCampaignDiscount(best.discountPerUnit);
  }

  /** 为购物车条目填充活动价（与商品列表/下单同一套规则） */
  public void fillCartItems(List<CartEntity> carts, Long userId) {
    if (carts == null || carts.isEmpty()) return;
    Context ctx = buildContext(userId);
    for (CartEntity cart : carts) {
      if (cart.getProductId() == null || cart.getPrice() == null) continue;
      ProductEntity product = productMapper.selectById(cart.getProductId());
      if (product == null) continue;
      ItemPrice price = priceItem(product, cart.getPrice(),
          cart.getQuantity() == null ? 1 : cart.getQuantity(), userId, ctx);
      if (price == null || price.campaign == null) continue;
      cart.setCampaignId(price.campaign.getId());
      cart.setCampaignName(price.campaign.getName());
      cart.setCampaignPrice(price.finalPrice);
      cart.setCampaignDiscount(price.discountPerUnit);
      cart.setCampaignTag(price.tag);
    }
  }

  /** 单商品计价（展示与下单共用同一套规则） */
  public ItemPrice priceItem(ProductEntity product, BigDecimal basePrice, int quantity,
      Long userId, Context ctx) {
    if (product == null || basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0 || quantity <= 0) {
      return null;
    }
    if (product.getId() != null && ctx.flashSaleProductIds.contains(product.getId())) {
      return null;
    }
    ItemPrice best = null;
    for (MarketingCampaignEntity c : ctx.campaigns) {
      String kind = resolveKind(c);
      if (kind == null || "THRESHOLD".equals(kind)) continue;
      if (!matchesScope(c, product)) continue;
      if (!hasRemaining(c, quantity, userId, ctx, null)) continue;

      BigDecimal value = kind.equals("PERCENT") ? parsePercentRate(c.getDiscountValue())
          : parseAmount(c.getDiscountValue());
      if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) continue;

      BigDecimal finalPrice = basePrice;
      if ("PERCENT".equals(kind)) {
        if (value.compareTo(BigDecimal.ONE) >= 0) continue;
        finalPrice = basePrice.multiply(value).setScale(2, RoundingMode.HALF_UP);
      } else {
        finalPrice = basePrice.subtract(value).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
      }
      if (finalPrice.compareTo(basePrice) >= 0) continue;

      BigDecimal discountPerUnit = basePrice.subtract(finalPrice).setScale(2, RoundingMode.HALF_UP);
      BigDecimal estimated = discountPerUnit.multiply(BigDecimal.valueOf(quantity));
      if (!hasRemaining(c, quantity, userId, ctx, estimated)) continue;

      ItemPrice candidate = new ItemPrice();
      candidate.campaign = c;
      candidate.campaignType = kind;
      candidate.campaignValue = value;
      candidate.minOrderAmount = BigDecimal.ZERO;
      candidate.originalPrice = basePrice;
      candidate.finalPrice = finalPrice;
      candidate.discountPerUnit = discountPerUnit;
      candidate.lineDiscount = estimated;
      candidate.quantity = quantity;
      candidate.tag = buildTag(c, kind, value, BigDecimal.ZERO);
      if (best == null || candidate.finalPrice.compareTo(best.finalPrice) < 0) {
        best = candidate;
      }
    }
    // 没有商品级活动时，如果命中满减活动，仍返回标签用于展示（不改价）
    if (best == null) {
      for (MarketingCampaignEntity c : ctx.campaigns) {
        if (!"THRESHOLD".equals(resolveKind(c)) || !matchesScope(c, product)) continue;
        if (!hasRemaining(c, quantity, userId, ctx, parseAmount(c.getDiscountValue()))) continue;
        BigDecimal amount = parseAmount(c.getDiscountValue());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) continue;
        BigDecimal minAmount = c.getMinOrderAmount() == null ? BigDecimal.ZERO : c.getMinOrderAmount();
        ItemPrice threshold = new ItemPrice();
        threshold.campaign = c;
        threshold.campaignType = "THRESHOLD";
        threshold.campaignValue = amount;
        threshold.minOrderAmount = minAmount;
        threshold.originalPrice = basePrice;
        threshold.finalPrice = basePrice;
        threshold.discountPerUnit = BigDecimal.ZERO;
        threshold.lineDiscount = BigDecimal.ZERO;
        threshold.quantity = quantity;
        threshold.tag = buildTag(c, "THRESHOLD", amount, minAmount);
        return threshold;
      }
    }
    return best;
  }

  /**
   * 整单计价：先应用商品级活动价，再计算满减；返回需要占用的活动额度。
   */
  public OrderPricing applyToOrder(Long userId, List<OrderItemEntity> items) {
    OrderPricing result = new OrderPricing();
    if (items == null || items.isEmpty()) return result;
    Context ctx = buildContext(userId);
    Map<Long, Reservation> reservationMap = new LinkedHashMap<>();

    // 1) 商品级活动（折扣/直降）
    for (OrderItemEntity item : items) {
      if (item.getPrice() == null || item.getQuantity() == null || item.getQuantity() <= 0) continue;
      ProductEntity product = item.getProductId() == null ? null : productMapper.selectById(item.getProductId());
      if (product == null) continue;
      ItemPrice price = priceItem(product, item.getPrice(), item.getQuantity(), userId, ctx);
      if (price == null || price.campaign == null || "THRESHOLD".equals(price.campaignType)) continue;

      item.setOriginalPrice(item.getPrice());
      item.setPrice(price.finalPrice);
      item.setCampaignId(price.campaign.getId());
      item.setCampaignDiscount(price.lineDiscount);

      result.totalDiscount = result.totalDiscount.add(price.lineDiscount);
      Reservation r = reservationMap.computeIfAbsent(price.campaign.getId(), id -> {
        Reservation n = new Reservation();
        n.campaignId = id;
        n.discountAmount = BigDecimal.ZERO;
        return n;
      });
      r.itemCount += item.getQuantity();
      r.discountAmount = r.discountAmount.add(price.lineDiscount);
      // 同一活动在本单内累计占用后，后续商品仍需校验剩余额度
      ctx.userUsed.merge(price.campaign.getId(), item.getQuantity(), Integer::sum);
    }

    // 2) 整单满减：只考虑未被商品级活动占用的适用商品
    MarketingCampaignEntity bestThreshold = null;
    BigDecimal bestDiscount = BigDecimal.ZERO;
    int bestCount = 0;
    for (MarketingCampaignEntity c : ctx.campaigns) {
      if (!"THRESHOLD".equals(resolveKind(c))) continue;
      BigDecimal amount = parseAmount(c.getDiscountValue());
      if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) continue;
      BigDecimal minAmount = c.getMinOrderAmount() == null ? BigDecimal.ZERO : c.getMinOrderAmount();
      BigDecimal eligibleAmount = BigDecimal.ZERO;
      int eligibleCount = 0;
      for (OrderItemEntity item : items) {
        if (item.getCampaignId() != null || item.getPrice() == null) continue;
        // 秒杀商品不参与营销活动：不计入满减门槛，避免秒杀价被再次参与满减
        if (item.getProductId() != null && ctx.flashSaleProductIds.contains(item.getProductId())) continue;
        ProductEntity product = item.getProductId() == null ? null : productMapper.selectById(item.getProductId());
        if (product == null || !matchesScope(c, product)) continue;
        eligibleAmount = eligibleAmount.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        eligibleCount += item.getQuantity();
      }
      if (eligibleCount <= 0 || eligibleAmount.compareTo(minAmount) < 0) continue;
      BigDecimal discount = amount.min(eligibleAmount);
      if (!hasRemaining(c, eligibleCount, userId, ctx, discount)) continue;
      if (discount.compareTo(bestDiscount) > 0) {
        bestThreshold = c;
        bestDiscount = discount;
        bestCount = eligibleCount;
      }
    }
    if (bestThreshold != null) {
      result.orderDiscount = bestDiscount;
      result.totalDiscount = result.totalDiscount.add(bestDiscount);
      Reservation r = reservationMap.computeIfAbsent(bestThreshold.getId(), id -> {
        Reservation n = new Reservation();
        n.campaignId = id;
        n.discountAmount = BigDecimal.ZERO;
        return n;
      });
      r.itemCount += bestCount;
      r.discountAmount = r.discountAmount.add(bestDiscount);
    }
    result.reservations = new ArrayList<>(reservationMap.values());
    return result;
  }

  /** 下单时占用活动额度（活动总量/预算/每人限购） */
  @Transactional
  public void reserveUsage(Long userId, Long orderId, String orderNo, OrderPricing pricing) {
    if (pricing == null || pricing.reservations == null || pricing.reservations.isEmpty()) return;
    for (Reservation r : pricing.reservations) {
      MarketingCampaignEntity c = campaignMapper.selectById(r.campaignId);
      if (c == null) {
        throw new BusinessException(400, "营销活动不存在或已删除");
      }
      int limitCount = Boolean.TRUE.equals(c.getStockLimit()) && c.getTotalCount() != null
          ? c.getTotalCount() : 0;
      BigDecimal limitAmount = c.getBudget() == null ? BigDecimal.ZERO : c.getBudget();
      int affected = campaignMapper.occupy(c.getId(), r.itemCount,
          r.discountAmount == null ? BigDecimal.ZERO : r.discountAmount, limitCount, limitAmount);
      if (affected == 0) {
        throw new BusinessException(400, "活动名额或预算已用完：" + c.getName());
      }
      // 按用户限购原子占用
      int perLimit = c.getPerLimit() == null ? 0 : c.getPerLimit();
      if (perLimit > 0 && userId != null) {
        userUsageMapper.initRow(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), c.getId(), userId);
        int userAffected = userUsageMapper.occupy(c.getId(), userId, r.itemCount, perLimit);
        if (userAffected == 0) {
          throw new BusinessException(400, "已达到活动每人限购数量：" + c.getName());
        }
      }
      CampaignUsageEntity usage = new CampaignUsageEntity();
      usage.setCampaignId(c.getId());
      usage.setUserId(userId);
      usage.setOrderId(orderId);
      usage.setOrderNo(orderNo);
      usage.setItemCount(r.itemCount);
      usage.setDiscountAmount(r.discountAmount == null ? BigDecimal.ZERO : r.discountAmount);
      usage.setStatus("LOCKED");
      usageMapper.insert(usage);
    }
  }

  /** 支付成功：用量转为正式消耗 */
  @Transactional
  public void confirmUsage(Long orderId) {
    if (orderId == null) return;
    usageMapper.update(null, new LambdaUpdateWrapper<CampaignUsageEntity>()
        .eq(CampaignUsageEntity::getOrderId, orderId)
        .eq(CampaignUsageEntity::getStatus, "LOCKED")
        .set(CampaignUsageEntity::getStatus, "CONFIRMED"));
  }

  /** 取消/超时：释放活动额度（幂等） */
  @Transactional
  public void releaseUsage(Long orderId) {
    if (orderId == null) return;
    List<CampaignUsageEntity> usages = usageMapper.selectList(
        new LambdaQueryWrapper<CampaignUsageEntity>()
            .eq(CampaignUsageEntity::getOrderId, orderId)
            .ne(CampaignUsageEntity::getStatus, "RELEASED"));
    for (CampaignUsageEntity u : usages) {
      campaignMapper.release(u.getCampaignId(), u.getItemCount() == null ? 0 : u.getItemCount(),
          u.getDiscountAmount() == null ? BigDecimal.ZERO : u.getDiscountAmount());
      MarketingCampaignEntity c = campaignMapper.selectById(u.getCampaignId());
      if (c != null && c.getPerLimit() != null && c.getPerLimit() > 0 && u.getUserId() != null) {
        userUsageMapper.release(u.getCampaignId(), u.getUserId(),
            u.getItemCount() == null ? 0 : u.getItemCount());
      }
      u.setStatus("RELEASED");
      usageMapper.updateById(u);
    }
  }

  /** 是否还有活动额度（展示/下单预校验） */
  private boolean hasRemaining(MarketingCampaignEntity c, int count, Long userId, Context ctx,
      BigDecimal estimatedDiscount) {
    if (Boolean.TRUE.equals(c.getStockLimit()) && c.getTotalCount() != null && c.getTotalCount() > 0) {
      int used = c.getUsedCount() == null ? 0 : c.getUsedCount();
      if (used + count > c.getTotalCount()) return false;
    }
    if (c.getBudget() != null && c.getBudget().compareTo(BigDecimal.ZERO) > 0 && estimatedDiscount != null) {
      BigDecimal used = c.getUsedAmount() == null ? BigDecimal.ZERO : c.getUsedAmount();
      if (used.add(estimatedDiscount).compareTo(c.getBudget()) > 0) return false;
    }
    if (c.getPerLimit() != null && c.getPerLimit() > 0 && userId != null) {
      int used = ctx.userUsed.getOrDefault(c.getId(), 0);
      if (used + count > c.getPerLimit()) return false;
    }
    return true;
  }

  /** 活动范围匹配 */
  private boolean matchesScope(MarketingCampaignEntity c, ProductEntity product) {
    String scope = c.getProductScope();
    if (scope == null || scope.isBlank() || SCOPE_ALL.equals(scope)) return true;
    if (SCOPE_PRODUCT.equals(scope)) {
      return containsId(c.getProductIds(), product.getId());
    }
    if (SCOPE_CATEGORY.equals(scope)) {
      return containsId(c.getProductCategoryIds(), product.getCategoryId());
    }
    return false;
  }

  private boolean containsId(String csv, Long id) {
    if (csv == null || csv.isBlank() || id == null) return false;
    for (String part : csv.split(",")) {
      if (part.trim().equals(String.valueOf(id))) return true;
    }
    return false;
  }

  /** 解析活动优惠类型 */
  private String resolveKind(MarketingCampaignEntity c) {
    String type = c.getType() == null ? "" : c.getType();
    String discount = c.getDiscountType() == null ? "" : c.getDiscountType();
    if ("秒杀".equals(type) || "拼团".equals(type)) return null;
    if ("满减".equals(discount) || "满减".equals(type)) return "THRESHOLD";
    if ("直降".equals(discount)) return "DIRECT";
    if ("百分比折扣".equals(discount) || "折扣".equals(type)) return "PERCENT";
    return null;
  }

  /** 解析百分比折扣为最终价格系数（0.7 = 7 折） */
  private BigDecimal parsePercentRate(String raw) {
    BigDecimal v = parseNumber(raw, "%", "折");
    if (v == null) return null;
    String s = raw == null ? "" : raw.trim();
    BigDecimal rate;
    if (s.contains("%")) {
      rate = BigDecimal.ONE.subtract(v.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    } else if (s.contains("折")) {
      rate = v.compareTo(BigDecimal.ONE) < 0 ? v
          : (v.compareTo(BigDecimal.TEN) <= 0 ? v.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP)
          : BigDecimal.ONE.subtract(v.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
    } else if (v.compareTo(BigDecimal.ONE) <= 0) {
      rate = v;
    } else if (v.compareTo(BigDecimal.TEN) <= 0) {
      rate = v.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
    } else {
      rate = BigDecimal.ONE.subtract(v.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }
    if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) return null;
    return rate;
  }

  /** 解析金额（直降/满减减免额） */
  private BigDecimal parseAmount(String raw) {
    BigDecimal v = parseNumber(raw, "¥", "元", "减");
    if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) return null;
    return v.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal parseNumber(String raw, String... removeTokens) {
    if (raw == null || raw.isBlank()) return null;
    String s = raw.trim();
    for (String token : removeTokens) {
      s = s.replace(token, "");
    }
    s = s.replace(" ", "").trim();
    if (s.isEmpty()) return null;
    try {
      return new BigDecimal(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 活动标签文案 */
  private String buildTag(MarketingCampaignEntity c, String kind, BigDecimal value, BigDecimal minAmount) {
    if ("PERCENT".equals(kind)) {
      BigDecimal zhe = value.multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
      String text = zhe.stripTrailingZeros().toPlainString();
      return "限时 " + text + " 折";
    }
    if ("DIRECT".equals(kind)) {
      return "立减 ¥" + value.stripTrailingZeros().toPlainString();
    }
    return "满 ¥" + minAmount.stripTrailingZeros().toPlainString()
        + " 减 ¥" + value.stripTrailingZeros().toPlainString();
  }
}

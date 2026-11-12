package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.order.OrderRefundRequest;
import com.moyuyo.common.dto.order.CreateOrderRequest;
import com.moyuyo.common.dto.order.CreatePresaleOrderRequest;
import com.moyuyo.common.dto.order.OrderItemRequest;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.admin.entity.FinanceRecordEntity;
import com.moyuyo.dao.admin.mapper.FinanceRecordMapper;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.LogisticsEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.PaymentEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductPresaleEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.entity.UserCouponEntity;
import com.moyuyo.dao.mapper.AddressMapper;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.LogisticsMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.PaymentMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductPresaleMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.CouponService;
import com.moyuyo.service.InviteService;
import com.moyuyo.service.MemberPriceService;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.NotificationService;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.RefundService;
import com.moyuyo.service.ShippingRateService;
import com.moyuyo.service.SubscribeService;
import com.moyuyo.service.WooCommerceSyncService;
import com.moyuyo.service.admin.SystemConfigService;
import com.moyuyo.service.config.MembershipRuleConfigService;
import com.moyuyo.service.mq.NotificationMessageProducer;
import com.moyuyo.service.support.ProductAttributes;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// 抑制 JDT null-analysis 对 MyBatis-Plus SFunction / Stream 方法引用的误报
// （底层 SFunction 的 @Nonnull 类型参数 vs Function.apply 形参推断冲突，mvn 编译无影响）
@SuppressWarnings("null")
public class OrderServiceImpl implements OrderService {

  private final OrderMapper orderMapper;
  private final OrderItemMapper orderItemMapper;
  private final PaymentMapper paymentMapper;
  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;
  // 财务流水 Mapper：支付成功后写入 mo_finance_record，供管理后台"交易流水"展示
  private final FinanceRecordMapper financeRecordMapper;
  // 地址表 Mapper：用于创建订单时快照地址详情，以及详情接口对历史订单按 addressId 回填
  private final AddressMapper addressMapper;
  // 物流表 Mapper：用户确认收货时同步写入 mo_logistics.received_at
  private final LogisticsMapper logisticsMapper;
  // 注入 WooCommerce 同步服务：付款回调完成后自动推送订单到 WooCommerce
  private final WooCommerceSyncService wooCommerceSyncService;
  // 任务中心埋点：付款回调后触发"完成 1 单购物 / 首单完成 / 累计消费满 500"
  private final MissionService missionService;
  private final NotificationMessageProducer notificationMessageProducer;
  private final NotificationService notificationService;
  // 消费返积分：付款回调后按 1 USD = 10 积分发放，首单 2 倍
  private final PointsRewardServiceImpl pointsRewardService;
  /** 邀请奖励：被邀请人首单支付成功后给双方发放积分 */
  private final InviteService inviteService;
  // 资金安全：下单优惠金额必须服务端重算（券核验/积分抵扣），不能信任前端传入数值
  private final CouponService couponService;
  private final MemberService memberService;
  // 会员价：按会员等级统一折扣（商品销售价 × 折扣系数），与优惠券叠加
  private final MemberPriceService memberPriceService;
  // 秒杀商品不参与会员折扣：下单时需排除进行中的秒杀商品
  private final FlashSaleMapper flashSaleMapper;
  // 资金安全：运费由服务端按 zone × method 重算覆盖前端传的 freight，防止改包刷价
  private final ShippingRateService shippingRateService;
  /** 积分抵扣换算/抵扣上限从会员规则配置读取（后台可调） */
  private final MembershipRuleConfigService ruleConfigService;
  // 营销活动执行：活动价、满减、限购/预算/总量占用
  private final CampaignPricingServiceImpl campaignPricingService;
  /** 系统配置：读取商品税率、运费类系数等后台可调参数 */
  private final SystemConfigService systemConfigService;
  // 商品预售配置：预售下单价格、限购、库存全部以该表为准
  private final ProductPresaleMapper productPresaleMapper;
  // 尾款逾期退定金：复用现有退款链路（生成退款单 + 调用原支付渠道 + 写财务流水）
  private final RefundService refundService;
  // 订阅订单：支付成功回调内开通/续期订阅（SubscribeServiceImpl 只依赖 dao mapper，不会循环依赖）
  private final SubscribeService subscribeService;

  // ===== 预售相关常量 =====
  private static final String ORDER_TYPE_NORMAL = "NORMAL";
  private static final String ORDER_TYPE_PRESALE = "PRESALE";
  // 积分兑换订单：0 元成交，直接进入待发货，财务/GMV 统计需按该类型排除
  private static final String ORDER_TYPE_POINTS = "POINTS";
  // 订阅订单：支付成功后开通/续期订阅，不进入发货流程
  private static final String ORDER_TYPE_SUBSCRIPTION = "SUBSCRIPTION";
  private static final String PRESALE_TYPE_DISPLAY = "DISPLAY";
  private static final String PRESALE_STAGE_FULL = "FULL";
  private static final String PRESALE_STAGE_DEPOSIT = "DEPOSIT";
  private static final String PRESALE_STAGE_BALANCE = "BALANCE";

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
    return createOrderInternal(userId, items, addressId, remark, couponId, couponDiscount,
            pointsUsed, pointsDiscount, shippingMethod, freight, null, null);
  }

  /**
   * 创建订单内部实现。
   *
   * @param knownMemberDiscount 调用方已计算的会员优惠金额；为 null 时在此处按会员等级计算
   * @param knownCampaignPricing 调用方已计算的活动计价；为 null 时在此处计算
   */
  private OrderEntity createOrderInternal(Long userId, List<OrderItemEntity> items, Long addressId, String remark,
                                          String couponId, BigDecimal couponDiscount, Integer pointsUsed,
                                          BigDecimal pointsDiscount, String shippingMethod, BigDecimal freight,
                                          BigDecimal knownMemberDiscount,
                                          CampaignPricingServiceImpl.OrderPricing knownCampaignPricing) {
    // 生产防护：禁止空商品列表创建零金额订单
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("订单商品不能为空");
    }
    // 营销活动计价：活动价 + 满减（调用方已计算时直接复用，避免重复查询）
    CampaignPricingServiceImpl.OrderPricing campaignPricing = knownCampaignPricing != null
        ? knownCampaignPricing : campaignPricingService.applyToOrder(userId, items);
    // 生成订单号: ORD + yyyyMMdd + 8位雪花ID后缀
    String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String snowId = String.valueOf(IdWorker.getId());
    String orderNo = "ORD" + datePart + snowId.substring(snowId.length() - 8);

    // 校验库存并计算商品总金额；M2 修复：非 SKU 商品也走原子 UPDATE 扣减 stock，防止并发超卖
    BigDecimal goodsAmount = BigDecimal.ZERO;
    // 缓存本次下单商品，供税费计算、虚拟商品判定复用，避免重复查库
    Map<Long, ProductEntity> productMap = new java.util.HashMap<>();
    for (OrderItemEntity item : items) {
      // 校验商品是否存在且在上架状态
      ProductEntity product = productMapper.selectById(item.getProductId());
      if (product == null) {
        throw new IllegalArgumentException("商品不存在: " + item.getProductId());
      }
      if (product.getOnSale() == null || !product.getOnSale()) {
        throw new IllegalArgumentException("商品已下架: " + product.getName());
      }
      productMap.put(product.getId(), product);
      Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
      int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
      // 单独出售：每个订单最多购买 1 件
      if (ProductAttributes.bool(attrs, "sold_individually", false) && quantity > 1) {
        throw new IllegalArgumentException("该商品每单限购 1 件: " + product.getName());
      }
      // 允许缺货订购（backorders = notify / yes）时放开库存校验，允许扣成负库存
      boolean allowBackorder = !"no".equalsIgnoreCase(ProductAttributes.str(attrs, "backorders", "no"));
      if (item.getSkuId() != null) {
        // 走 SKU 原子扣减
        ProductSkuEntity sku = productSkuMapper.selectById(item.getSkuId());
        if (sku == null) {
          throw new IllegalArgumentException("SKU不存在: " + item.getSkuId());
        }
        // 原子扣减：UPDATE mo_product_sku SET stock = stock - qty WHERE id = ? [AND stock >= qty]
        LambdaUpdateWrapper<ProductSkuEntity> stockWrapper = new LambdaUpdateWrapper<>();
        stockWrapper.eq(ProductSkuEntity::getId, item.getSkuId())
            .setSql("stock = stock - " + quantity);
        if (!allowBackorder) {
          stockWrapper.apply("stock >= {0}", quantity);
        }
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
            .setSql("stock = stock - " + quantity);
        if (!allowBackorder) {
          productStockWrapper.apply("stock >= {0}", quantity);
        }
        int affected = productMapper.update(null, productStockWrapper);
        if (affected == 0) {
          throw new IllegalStateException("商品库存不足: " + product.getName());
        }
      }
      goodsAmount = goodsAmount.add(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
    }

    // 金额兜底：null 转 0，避免 NPE 与下游 BigDecimal 计算异常
    BigDecimal safeCouponDiscount = couponDiscount == null ? BigDecimal.ZERO : couponDiscount;
    BigDecimal safePointsDiscount = pointsDiscount == null ? BigDecimal.ZERO : pointsDiscount;
    int safePointsUsed = pointsUsed == null ? 0 : Math.max(pointsUsed, 0);

    // 服务端权威运费：按 (country, shippingMethod) 重算覆盖前端传的 freight
    // 防改包刷价；不可达 zone（未配置运费规则）时降级用前端传入值（不阻断下单）
    // 全虚拟商品订单不产生运费（无需物流）
    BigDecimal safeFreight = isAllVirtual(items, productMap)
        ? BigDecimal.ZERO
        : resolveServerFreight(userId, addressId, items, goodsAmount, shippingMethod, freight);

    // 税费：按商品 tax_status / tax_class 与服务端配置的税率计算
    // 会员价：按会员等级统一折扣，作用于商品销售价，与优惠券叠加；秒杀商品不参与
    // 调用方（createOrderFromRequest）已计算过则直接复用，避免重复查询
    BigDecimal safeMemberDiscount = knownMemberDiscount != null
        ? knownMemberDiscount : computeMemberDiscount(userId, items);

    // 税基按「优惠后金额」计算：会员价/活动满减/优惠券分摊扣减；积分属于支付抵扣，不降低税基
    BigDecimal goodsDiscount = safeMemberDiscount
        .add(campaignPricing.orderDiscount)
        .add(safeCouponDiscount);
    BigDecimal taxAmount = computeTaxAmount(items, safeFreight, productMap, goodsAmount, goodsDiscount);

    // M1 修复：payAmount = 商品总金额 + 运费 + 税费 - 会员优惠 - 营销活动满减 - 优惠券减免 - 积分抵扣，下限 0
    // 商品级活动价已在 items.price 中体现，这里只需再减整单满减
    BigDecimal payAmount = goodsAmount
            .subtract(safeMemberDiscount)
            .add(safeFreight)
            .add(taxAmount)
            .subtract(safeCouponDiscount)
            .subtract(safePointsDiscount)
            .subtract(campaignPricing.orderDiscount);
    if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
      payAmount = BigDecimal.ZERO;
    }

    // 构建订单
    OrderEntity order = new OrderEntity();
    order.setOrderNo(orderNo);
    order.setUserId(userId);
    order.setGoodsAmount(goodsAmount);
    order.setFreight(safeFreight);
    order.setTaxAmount(taxAmount);
    order.setCouponDiscount(safeCouponDiscount);
    order.setMemberDiscount(safeMemberDiscount);
    order.setCampaignDiscount(campaignPricing.totalDiscount);
    // 单独记录整单满减，供订单详情把活动优惠计入金额合计（商品级活动价已体现在订单项价格里）
    order.setOrderCampaignDiscount(campaignPricing.orderDiscount);
    order.setPointsDiscount(safePointsDiscount);
    order.setPointsUsed(safePointsUsed);
    order.setPayAmount(payAmount);
    order.setStatus(PENDING_PAY.name());
    // 普通订单显式落库订单类型，避免依赖数据库默认值
    order.setOrderType(ORDER_TYPE_NORMAL);
    order.setAddressId(addressId);
    order.setRemark(remark);
    order.setCouponId(couponId);
    order.setShippingMethod(shippingMethod == null ? "standard" : shippingMethod);
    order.setDeleteStatus(0);

    // 按 addressId 查询地址并快照收件人/电话/详细地址到订单表，
    // 防止用户后续修改或删除地址后，订单的历史收货信息丢失。
    snapshotAddress(order, addressId, userId);

    orderMapper.insert(order);

    // 批量保存订单项
    for (OrderItemEntity item : items) {
      item.setOrderId(order.getId());
      item.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
      orderItemMapper.insert(item);
    }

    // 占用活动额度：活动总量/预算/每人限购，取消或超时自动释放
    campaignPricingService.reserveUsage(userId, order.getId(), order.getOrderNo(), campaignPricing);

    return order;
  }

  @Override
  @Transactional
  public OrderEntity createOrderFromRequest(Long userId, CreateOrderRequest request) {
    // 将请求中的商品信息转换为订单项实体（含 SKU/商品校验）
    List<OrderItemEntity> items = new ArrayList<>();
    // 商品级积分抵扣开关：订单中存在任一关闭该开关的商品时，整单不允许积分抵扣
    boolean pointsDeductAllowed = true;
    for (OrderItemRequest itemReq : request.getItems()) {
      ProductSkuEntity sku = productSkuMapper.selectById(itemReq.getSkuId());
      ProductEntity product = productMapper.selectById(itemReq.getProductId());
      if (product == null) {
        throw new IllegalArgumentException("商品不存在: " + itemReq.getProductId());
      }
      if (Boolean.FALSE.equals(product.getAllowPointsDeduct())) {
        pointsDeductAllowed = false;
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
      // 服务端权威价：销售期外回退常规价，避免前端缓存/改包使用过期促销价
      Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
      item.setPrice(ProductAttributes.effectivePrice(
          attrs,
          sku != null ? sku.getSkuCode() : null,
          sku != null ? sku.getSpec() : null,
          item.getPrice(),
          product.getOriginalPrice()));
      items.add(item);
    }

    // 预售商品（全款/定金）必须走预售下单接口，避免按现货价购买预售商品造成价格穿透。
    // 展示型预售不改变价格与下单流程，仍允许普通下单。
    assertNoActivePresaleProduct(items);

    // 营销活动计价：活动价 + 满减（必须在优惠券/会员/积分之前计算，保证按活动后金额算门槛）
    CampaignPricingServiceImpl.OrderPricing campaignPricing =
        campaignPricingService.applyToOrder(userId, items);

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
    // 3) 会员价：先计算出来，供积分抵扣封顶使用，避免积分把订单抵扣成负数后仍扣满积分
    BigDecimal serverMemberDiscount = computeMemberDiscount(userId, items);
    // 4) 积分抵扣：100 积分 = 1 元，最高抵扣小计 30%，且不得超过"会员/优惠券抵扣后的剩余商品金额"。
    //    语义：最终抵扣 = min(客户端请求抵扣额, 比例上限 30%, 余额上限, 剩余商品金额)。
    BigDecimal serverPointsDiscount = BigDecimal.ZERO;
    int serverPointsUsed = 0;
    boolean requestedPoints = (request.getPointsUsed() != null && request.getPointsUsed() > 0)
        || (request.getPointsDiscount() != null
            && request.getPointsDiscount().compareTo(BigDecimal.ZERO) > 0);
    // 请求了积分但订单含不支持积分的商品：直接拒绝，避免静默丢弃积分导致实付金额高于结算页展示
    if (requestedPoints && !pointsDeductAllowed) {
      throw new com.moyuyo.common.exception.BusinessException(400,
          "订单中存在不支持积分抵扣的商品，请返回结算页刷新后重试");
    }
    boolean wantsPoints = pointsDeductAllowed && requestedPoints;
    if (wantsPoints) {
      int balance = Math.max(memberService.getPointsBalance(userId), 0);
      // 积分抵扣规则：后台可配置（N 积分 = 1 美元，最高抵扣商品小计的 X%）
      MembershipRuleConfigService.MembershipRuleConfig rule = ruleConfigService.getConfig();
      BigDecimal pointsPerUnit = BigDecimal.valueOf(rule.pointsPerCurrencyUnit());
      // 比例上限：商品小计 X%（HALF_UP）
      BigDecimal maxByRate = goodsSubtotal.multiply(rule.maxDeductRate())
          .setScale(2, java.math.RoundingMode.HALF_UP);
      // 余额上限：可用积分按 N 积分 = 1 美元折算（DOWN）
      BigDecimal maxByBalance = BigDecimal.valueOf(balance)
          .divide(pointsPerUnit, 2, java.math.RoundingMode.DOWN);
      // 剩余金额上限：会员优惠 + 优惠券 + 满减后剩余的商品金额（运费不允许用积分抵扣）
      BigDecimal maxByRemaining = goodsSubtotal
          .subtract(serverMemberDiscount)
          .subtract(serverCouponDiscount)
          .subtract(campaignPricing.orderDiscount)
          .max(BigDecimal.ZERO);

      // 客户端"请求抵扣额"：优先取 pointsDiscount（前端结算页实际展示、并用于计算 total 的金额），
      // 其次才用 pointsUsed 折算。为什么优先金额而非优先积分数（重要，涉及回归红线）：
      // 前端 usedPoints = Math.floor(pointsDiscount * 100)，而 pointsDiscount = min(subtotal*0.3,
      // balance/100) 是**未取整的浮点数**；当 0.3×subtotal 落到 19.43999… / 9.99899… 这类值时，
      // floor 后再 /100 会比页面展示值低 1 分（实测：subtotal=64.8→19.43 而页面为 19.44；
      // 33.33→9.99 而页面为 10.00）。若优先 pointsUsed，"按满额提交"这一路径会把 pay_amount
      // 抬高 1 分，破坏"与结算页逐分对齐"的红线。改用 pointsDiscount(金额) 取 HALF_UP 到分后，
      // 满额提交时恰等于比例上限 → 结果与改动前逐分一致（98/97/64.8/33.33 均已验证）。
      BigDecimal requested;
      if (request.getPointsDiscount() != null
          && request.getPointsDiscount().compareTo(BigDecimal.ZERO) > 0) {
        requested = request.getPointsDiscount().setScale(2, java.math.RoundingMode.HALF_UP);
      } else if (request.getPointsUsed() != null && request.getPointsUsed() > 0) {
        requested = BigDecimal.valueOf(request.getPointsUsed())
            .divide(pointsPerUnit, 2, java.math.RoundingMode.DOWN);
      } else {
        requested = null;
      }
      // 四值取 min：客户端请求值 与 比例/余额/剩余金额三个上限
      BigDecimal cap = maxByRate.min(maxByBalance).min(maxByRemaining);
      serverPointsDiscount = requested == null ? cap : requested.min(cap);
      serverPointsUsed = serverPointsDiscount
          .multiply(pointsPerUnit)
          .setScale(0, java.math.RoundingMode.DOWN).intValue();

      // 2b：勾选了积分但服务端算下来可用抵扣为 0。
      // - 若仍有剩余商品金额、但积分余额为 0（被其它待付款订单占用/耗尽）→ 不能静默按 0 积分成单，抛异常；
      // - 若订单已被会员/优惠券抵扣到 0（maxByRemaining=0），积分本就不该再扣，按 0 积分继续成单。
      if (serverPointsUsed <= 0) {
        if (maxByRemaining.compareTo(BigDecimal.ZERO) > 0
            && maxByBalance.compareTo(BigDecimal.ZERO) <= 0) {
          throw new com.moyuyo.common.exception.BusinessException(400, "可用积分不足，请返回结算页刷新后重试");
        }
        serverPointsDiscount = BigDecimal.ZERO;
        serverPointsUsed = 0;
      }
    }
    BigDecimal serverFreight = request.getFreight() == null ? BigDecimal.ZERO : request.getFreight();

    // 落库（同一事务内）并"占用"优惠券 + "冻结"积分，避免同一张券/同一批积分被多笔待支付订单同时抵扣。
    // couponId 传 null：券模板 id 一律由服务端从 userCoupon 记录推导后落库，绝不信任前端传入的 code/id。
    OrderEntity order = createOrderInternal(userId, items, request.getAddressId(), request.getRemark(),
            null, serverCouponDiscount,
            serverPointsUsed, serverPointsDiscount,
            request.getShippingMethod(), serverFreight, serverMemberDiscount, campaignPricing);

    // 优惠券：以服务端 couponUserId 为准（不信任前端 couponId/couponDiscount）
    if (serverCouponDiscount.compareTo(BigDecimal.ZERO) > 0 && request.getCouponUserId() != null) {
        // 记录用券明细；couponId(券模板 id)由服务端从用户券记录推导后落库，
        // 不再依赖前端传的 code（mo_coupon.code 全为 NULL，这条链路本就是死的）
        UserCouponEntity uc = couponService.getUserCoupon(userId, request.getCouponUserId());
        order.setUserCouponId(uc.getId());
        order.setCouponId(uc.getCouponId() == null ? null : String.valueOf(uc.getCouponId()));
        orderMapper.updateById(order);
        // 占用券（UNUSED→LOCKED，写 used_order_id）；抢不到抛异常 → 整个下单事务回滚（含库存）
        couponService.useCoupon(userId, uc.getId(), order.getId());
        log.info("订单优惠券已占用: orderId={}, userCouponId={}, couponId={}",
                order.getId(), uc.getId(), uc.getCouponId());
    }

    // 积分：下单即"冻结"（占用），支付成功转实扣，取消/超时解冻回退。
    // 用条件 UPDATE 原子冻结，两笔待付款订单无法占用同一批积分；冻结失败 → 回滚整单。
    if (serverPointsUsed > 0) {
        boolean frozen = memberService.freezePoints(userId, serverPointsUsed,
                order.getOrderNo(), "订单积分抵扣冻结：" + order.getOrderNo());
        if (!frozen) {
            throw new com.moyuyo.common.exception.BusinessException(400, "可用积分不足，请刷新后重试");
        }
        log.info("订单抵扣积分已冻结: orderId={}, orderNo={}, pointsUsed={}",
                order.getId(), order.getOrderNo(), serverPointsUsed);
    }
    return order;
  }

  @Override
  @Transactional
  public OrderEntity createPointsExchangeOrder(Long userId, Long productId, Integer quantity,
      Long skuId, Long addressId, int pointsCost) {
    ProductEntity product = productMapper.selectById(productId);
    if (product == null) {
      throw new IllegalArgumentException("商品不存在: " + productId);
    }
    if (product.getOnSale() == null || !product.getOnSale()) {
      throw new IllegalArgumentException("商品已下架: " + product.getName());
    }
    if (!Boolean.TRUE.equals(product.getAllowPointsExchange())
        || product.getPointsPrice() == null || product.getPointsPrice() <= 0) {
      throw new IllegalArgumentException("该商品未开放积分兑换");
    }
    // 变体商品必须绑定 SKU：后台按 SKU 拣货发货，订单明细缺规格会导致无法出库
    ProductSkuEntity sku = null;
    if (skuId != null) {
      sku = productSkuMapper.selectById(skuId);
      if (sku == null || !productId.equals(sku.getProductId())) {
        throw new IllegalArgumentException("商品规格不存在: " + skuId);
      }
    }
    int qty = quantity == null || quantity <= 0 ? 1 : quantity;

    // 积分专属库存原子扣减：-1 表示不限不扣减；其余值需保证扣后不为负，防止并发超兑
    Integer pointsStock = product.getPointsStock();
    if (pointsStock != null && pointsStock >= 0) {
      int affected = productMapper.update(null,
          new LambdaUpdateWrapper<ProductEntity>()
              .eq(ProductEntity::getId, productId)
              .setSql("points_stock = points_stock - " + qty)
              .apply("points_stock >= {0}", qty));
      if (affected == 0) {
        throw new IllegalStateException("积分库存不足");
      }
    }

    // 订单号用 PTS 前缀与普通订单区分，便于财务/GMV 报表按前缀与 order_type 排除
    String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String snowId = String.valueOf(IdWorker.getId());
    String orderNo = "PTS" + datePart + snowId.substring(snowId.length() - 8);

    OrderEntity order = new OrderEntity();
    order.setOrderNo(orderNo);
    order.setUserId(userId);
    // 积分商品不产生任何金额，避免污染 GMV 与财务结算
    order.setGoodsAmount(BigDecimal.ZERO);
    order.setFreight(BigDecimal.ZERO);
    order.setTaxAmount(BigDecimal.ZERO);
    order.setCouponDiscount(BigDecimal.ZERO);
    order.setMemberDiscount(BigDecimal.ZERO);
    order.setCampaignDiscount(BigDecimal.ZERO);
    order.setOrderCampaignDiscount(BigDecimal.ZERO);
    order.setPointsDiscount(BigDecimal.ZERO);
    order.setPointsUsed(pointsCost);
    order.setPayAmount(BigDecimal.ZERO);
    order.setPayChannel("POINTS");
    // 积分已即时扣减，订单直接视为已支付并进入待发货
    order.setStatus(PENDING_SHIP.name());
    order.setPaidAt(LocalDateTime.now());
    order.setOrderType(ORDER_TYPE_POINTS);
    order.setAddressId(addressId);
    order.setShippingMethod("standard");
    order.setDeleteStatus(0);
    // 标记为已同步：积分订单是站内 0 元履约单，不能出现在 WooCommerce 待补推队列中
    // （syncAllPendingOrders 会捞 sync_status=-1 或 woo_order_id 为空的订单）
    order.setSyncStatus(1);
    snapshotAddress(order, addressId, userId);
    orderMapper.insert(order);

    // 订单项单价 0，仅承载商品信息，供后台按 SKU 发货、售后与订单详情展示
    OrderItemEntity item = new OrderItemEntity();
    item.setOrderId(order.getId());
    item.setProductId(product.getId());
    item.setSkuId(sku == null ? null : sku.getId());
    item.setSkuSpec(sku == null ? null : sku.getSpec());
    item.setProductName(product.getName());
    item.setMainImage(product.getMainImage());
    item.setPrice(BigDecimal.ZERO);
    item.setQuantity(qty);
    item.setSubtotal(BigDecimal.ZERO);
    orderItemMapper.insert(item);

    log.info("积分兑换订单已创建: orderNo={}, userId={}, productId={}, points={}",
        orderNo, userId, productId, pointsCost);
    return order;
  }

  // ==================== 预售订单 ====================

  @Override
  @Transactional
  public OrderEntity createPresaleOrder(Long userId, CreatePresaleOrderRequest request) {
    ProductEntity product = productMapper.selectById(request.getProductId());
    if (product == null) {
      throw new IllegalArgumentException("商品不存在: " + request.getProductId());
    }
    if (product.getOnSale() == null || !product.getOnSale()) {
      throw new IllegalArgumentException("商品已下架: " + product.getName());
    }
    ProductPresaleEntity presale = getPresaleByProductId(product.getId());
    if (presale == null) {
      throw new IllegalArgumentException("该商品未配置预售");
    }
    String presaleType = presale.getPresaleType();
    if (PRESALE_TYPE_DISPLAY.equalsIgnoreCase(presaleType)) {
      throw new IllegalArgumentException("展示型预售不改变下单价格，请按普通商品下单");
    }
    if (!PRESALE_STAGE_FULL.equalsIgnoreCase(presaleType) && !PRESALE_STAGE_DEPOSIT.equalsIgnoreCase(presaleType)) {
      throw new IllegalArgumentException("不支持的预售类型: " + presaleType);
    }
    // 预售窗口校验：草稿/已结束/未开始/已过期都不允许下单
    LocalDateTime now = LocalDateTime.now();
    if ("DRAFT".equals(presale.getStatus())) {
      throw new IllegalArgumentException("预售尚未发布");
    }
    if ("ENDED".equals(presale.getStatus())) {
      throw new IllegalArgumentException("预售已结束");
    }
    if (presale.getStartTime() != null && now.isBefore(presale.getStartTime())) {
      throw new IllegalArgumentException("预售尚未开始");
    }
    if (presale.getEndTime() != null && now.isAfter(presale.getEndTime())) {
      throw new IllegalArgumentException("预售已结束");
    }
    int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
    if (quantity <= 0) {
      throw new IllegalArgumentException("购买数量必须大于 0");
    }

    // 每人限购：统计该用户在当前预售下未取消订单的购买数量
    checkPresaleLimit(userId, presale, quantity);

    // 原子占用预售库存（sold_count + 上限校验），失败即库存不足
    reservePresaleStock(presale.getId(), quantity, product.getName());

    // SKU 仅用于记录规格与履约，不扣减 SKU 库存（预售卖的是未来货）
    ProductSkuEntity sku = null;
    if (request.getSkuId() != null && !request.getSkuId().equals(product.getId())) {
      sku = productSkuMapper.selectById(request.getSkuId());
      if (sku == null || !product.getId().equals(sku.getProductId())) {
        throw new IllegalArgumentException("SKU不存在: " + request.getSkuId());
      }
    }

    boolean isDeposit = PRESALE_STAGE_DEPOSIT.equalsIgnoreCase(presaleType);
    BigDecimal unitPrice = isDeposit ? presale.getDepositAmount() : presale.getPresalePrice();
    // 配置缺失时直接失败，避免空金额下单或 NPE
    if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(isDeposit ? "预售定金配置异常" : "预售价格配置异常");
    }

    OrderItemEntity item = new OrderItemEntity();
    item.setProductId(product.getId());
    item.setSkuId(sku != null ? sku.getId() : null);
    item.setProductName(product.getName());
    item.setMainImage(product.getMainImage());
    item.setSkuSpec(sku != null ? sku.getSpec() : null);
    item.setPrice(unitPrice);
    item.setQuantity(quantity);

    return insertPresaleOrder(userId, product, item, request.getAddressId(), request.getRemark(),
        request.getShippingMethod(), presale, isDeposit ? PRESALE_STAGE_DEPOSIT : PRESALE_STAGE_FULL, null);
  }

  @Override
  @Transactional
  public OrderEntity createBalanceOrder(Long depositOrderId, Long userId) {
    // FOR UPDATE 锁定定金单：串行化同一笔定金的尾款单创建，避免双击/并发产生两笔尾款单
    OrderEntity depositOrder = orderMapper.selectOne(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getId, depositOrderId)
            .last("FOR UPDATE"));
    if (depositOrder == null || !depositOrder.isPresale()
        || !PRESALE_STAGE_DEPOSIT.equals(depositOrder.getPresaleStage())) {
      throw new IllegalArgumentException("定金订单不存在或不支持支付尾款");
    }
    // 仅允许操作本人定金单：userId 为空（非用户态调用）一律拒绝，避免绕过归属校验
    if (userId == null || !userId.equals(depositOrder.getUserId())) {
      throw new IllegalArgumentException("无权操作该订单");
    }
    // 幂等：已存在未取消的尾款单时直接返回，避免重复点击生成多笔尾款
    OrderEntity existing = orderMapper.selectOne(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getParentOrderId, depositOrderId)
            .ne(OrderEntity::getStatus, CANCELLED.name())
            .last("LIMIT 1"));
    if (existing != null) {
      return existing;
    }
    // 定金必须已支付，否则不能进入尾款环节
    if (!depositOrder.isPaid()) {
      throw new IllegalStateException("定金尚未支付，暂不能支付尾款");
    }
    ProductPresaleEntity presale = productPresaleMapper.selectById(depositOrder.getPresaleId());
    if (presale == null) {
      throw new IllegalArgumentException("预售配置不存在");
    }
    LocalDateTime now = LocalDateTime.now();
    if (presale.getBalanceStart() != null && now.isBefore(presale.getBalanceStart())) {
      throw new IllegalStateException("尾款支付尚未开始");
    }
    if (presale.getBalanceEnd() != null && now.isAfter(presale.getBalanceEnd())) {
      throw new IllegalStateException("尾款支付已结束");
    }
    BigDecimal depositOffset = presale.getDepositOffset() != null
        ? presale.getDepositOffset() : presale.getDepositAmount();
    if (presale.getPresalePrice() == null || depositOffset == null) {
      throw new IllegalStateException("预售价格配置异常");
    }
    BigDecimal balanceUnit = presale.getPresalePrice().subtract(depositOffset);
    if (balanceUnit.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalStateException("尾款金额必须大于 0");
    }
    List<OrderItemEntity> depositItems = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
            .eq(OrderItemEntity::getOrderId, depositOrderId));
    if (depositItems == null || depositItems.isEmpty()) {
      throw new IllegalStateException("定金订单商品信息缺失");
    }
    OrderItemEntity depositItem = depositItems.get(0);
    ProductEntity product = productMapper.selectById(depositItem.getProductId());

    // 复制定金单商品行，价格替换为尾款单价（预售价 - 定金抵扣）
    OrderItemEntity balanceItem = new OrderItemEntity();
    balanceItem.setProductId(depositItem.getProductId());
    balanceItem.setSkuId(depositItem.getSkuId());
    balanceItem.setProductName(depositItem.getProductName());
    balanceItem.setMainImage(depositItem.getMainImage());
    balanceItem.setSkuSpec(depositItem.getSkuSpec());
    balanceItem.setPrice(balanceUnit);
    balanceItem.setQuantity(depositItem.getQuantity());

    return insertPresaleOrder(depositOrder.getUserId(), product, balanceItem,
        depositOrder.getAddressId(), depositOrder.getRemark(), depositOrder.getShippingMethod(),
        presale, PRESALE_STAGE_BALANCE, depositOrder.getId());
  }

  @Override
  @Transactional
  public boolean handleExpiredDepositOrder(Long depositOrderId, String reason) {
    // 行锁：与用户支付尾款、退款并发时串行化，避免"退款成功但尾款同时支付"
    OrderEntity depositOrder = orderMapper.selectOne(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getId, depositOrderId)
            .last("FOR UPDATE"));
    if (depositOrder == null || !depositOrder.isPresale()
        || !PRESALE_STAGE_DEPOSIT.equals(depositOrder.getPresaleStage())) {
      return false;
    }
    // 只处理"定金已支付、尚未退款/关闭"的订单；REFUNDED/CANCELLED 天然幂等跳过
    if (!PAID.name().equals(depositOrder.getStatus())) {
      return false;
    }
    ProductPresaleEntity presale = productPresaleMapper.selectById(depositOrder.getPresaleId());
    if (presale == null || presale.getBalanceEnd() == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    if (now.isBefore(presale.getBalanceEnd())) {
      return false;
    }
    // 已有生效尾款单（已支付/已发货等）说明预售正常履约，不再退定金
    List<OrderEntity> balanceOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getParentOrderId, depositOrder.getId()));
    for (OrderEntity balance : balanceOrders) {
      String balanceStatus = balance.getStatus();
      if (CANCELLED.name().equals(balanceStatus)) {
        continue;
      }
      if (!PENDING_PAY.name().equals(balanceStatus)) {
        return false;
      }
      // 尾款期已结束：关闭未支付的尾款单，防止退款后用户还能继续支付
      int cancelled = orderMapper.update(null, new LambdaUpdateWrapper<OrderEntity>()
          .eq(OrderEntity::getId, balance.getId())
          .eq(OrderEntity::getStatus, PENDING_PAY.name())
          .set(OrderEntity::getStatus, CANCELLED.name())
          .set(OrderEntity::getCancelTime, now)
          .set(OrderEntity::getCancelReason, "尾款期结束，尾款单自动关闭"));
      if (cancelled == 0) {
        // 尾款单刚被支付（与支付回调并发）：本单已履约，不能退定金，留待下轮按已支付尾款单跳过
        return false;
      }
    }

    // deposit_refundable 仅显式 false 才视为"定金不退"，历史数据为 null 时按可退处理
    boolean refundable = !Boolean.FALSE.equals(presale.getDepositRefundable());
    if (refundable) {
      OrderRefundRequest refundRequest = new OrderRefundRequest();
      refundRequest.setMode("AUTO");
      refundRequest.setAmount(depositOrder.getPayAmount());
      refundRequest.setReason(reason);
      refundRequest.setDescription("尾款期结束仍未支付尾款，自动原路退还定金");
      // operatorId=null：系统自动退款；退款完成后订单状态由退款链路置为 REFUNDED
      refundService.adminRefundOrder(depositOrder.getId(), refundRequest, null);
      // 补充关闭原因，便于售后/客服追溯订单被关闭的原因
      orderMapper.update(null, new LambdaUpdateWrapper<OrderEntity>()
          .eq(OrderEntity::getId, depositOrder.getId())
          .set(OrderEntity::getCancelTime, now)
          .set(OrderEntity::getCancelReason, reason));
    } else {
      int closed = orderMapper.update(null, new LambdaUpdateWrapper<OrderEntity>()
          .eq(OrderEntity::getId, depositOrder.getId())
          .eq(OrderEntity::getStatus, PAID.name())
          .set(OrderEntity::getStatus, CANCELLED.name())
          .set(OrderEntity::getCancelTime, now)
          .set(OrderEntity::getCancelReason, reason + "（定金按约定不予退还）"));
      if (closed == 0) {
        // 状态已被其他流程改变：不释放库存、不发通知，避免重复处理
        return false;
      }
      try {
        notificationService.notifyUser(depositOrder.getUserId(), "ORDER", "ORDER",
            "预售订单已关闭",
            "您的预售定金订单 " + depositOrder.getOrderNo()
                + " 尾款期已结束且未支付尾款，订单已关闭，定金按约定不予退还",
            depositOrder.getId());
      } catch (Exception e) {
        log.warn("[presale] 定金不退通知发送失败 orderNo={}, reason={}",
            depositOrder.getOrderNo(), e.getMessage());
      }
    }

    // 交易终止：释放定金单占用的预售库存（尾款单不占用库存，无需释放）
    releasePresaleStock(depositOrder);
    log.info("[presale] 尾款逾期处理完成 orderNo={}, refundable={}, payAmount={}",
        depositOrder.getOrderNo(), refundable, depositOrder.getPayAmount());
    return true;
  }

  /**
   * 预售订单落库。
   * <p>
   * 定金单只收定金（不含运费/税费），尾款单与全款单按地址重新计算运费与税费。
   * 预售订单不占用商品/SKU 现货库存，库存由 mo_product_presale.sold_count 单独维护。
   */
  private OrderEntity insertPresaleOrder(Long userId, ProductEntity product, OrderItemEntity item,
                                         Long addressId, String remark, String shippingMethod,
                                         ProductPresaleEntity presale, String stage, Long parentOrderId) {
    int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
    BigDecimal goodsAmount = item.getPrice().multiply(BigDecimal.valueOf(quantity));
    BigDecimal freight = BigDecimal.ZERO;
    BigDecimal taxAmount = BigDecimal.ZERO;
    // 定金单不合并运费/税费，留到尾款单收取，避免用户为未确定的履约重复付费
    if (!PRESALE_STAGE_DEPOSIT.equals(stage) && product != null) {
      Map<Long, ProductEntity> productMap = new java.util.HashMap<>();
      productMap.put(product.getId(), product);
      List<OrderItemEntity> singleItem = Collections.singletonList(item);
      freight = isAllVirtual(singleItem, productMap) ? BigDecimal.ZERO
          : resolveServerFreight(userId, addressId, singleItem, goodsAmount, shippingMethod, BigDecimal.ZERO);
      taxAmount = computeTaxAmount(singleItem, freight, productMap, goodsAmount, BigDecimal.ZERO);
    }
    BigDecimal payAmount = goodsAmount.add(freight).add(taxAmount);
    if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
      payAmount = BigDecimal.ZERO;
    }

    OrderEntity order = new OrderEntity();
    order.setOrderNo(generateOrderNo());
    order.setUserId(userId);
    order.setGoodsAmount(goodsAmount);
    order.setFreight(freight);
    order.setTaxAmount(taxAmount);
    order.setCouponDiscount(BigDecimal.ZERO);
    order.setMemberDiscount(BigDecimal.ZERO);
    order.setCampaignDiscount(BigDecimal.ZERO);
    order.setOrderCampaignDiscount(BigDecimal.ZERO);
    order.setPointsDiscount(BigDecimal.ZERO);
    order.setPointsUsed(0);
    order.setPayAmount(payAmount);
    order.setStatus(PENDING_PAY.name());
    order.setOrderType(ORDER_TYPE_PRESALE);
    order.setPresaleId(presale.getId());
    order.setPresaleStage(stage);
    order.setParentOrderId(parentOrderId);
    order.setAddressId(addressId);
    order.setRemark(remark);
    order.setShippingMethod(shippingMethod == null ? "standard" : shippingMethod);
    order.setDeleteStatus(0);
    snapshotAddress(order, addressId, userId);
    orderMapper.insert(order);

    item.setOrderId(order.getId());
    item.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
    orderItemMapper.insert(item);
    return order;
  }

  /** 按商品 ID 查预售配置（1:1） */
  private ProductPresaleEntity getPresaleByProductId(Long productId) {
    if (productId == null) {
      return null;
    }
    return productPresaleMapper.selectOne(
        new LambdaQueryWrapper<ProductPresaleEntity>()
            .eq(ProductPresaleEntity::getProductId, productId)
            .last("LIMIT 1"));
  }

  /** 普通下单拦截：全款/定金预售进行中时，禁止绕过预售入口按现货价下单 */
  private void assertNoActivePresaleProduct(List<OrderItemEntity> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    List<Long> productIds = items.stream()
        .map(OrderItemEntity::getProductId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    if (productIds.isEmpty()) {
      return;
    }
    List<ProductPresaleEntity> presales = productPresaleMapper.selectList(
        new LambdaQueryWrapper<ProductPresaleEntity>()
            .in(ProductPresaleEntity::getProductId, productIds));
    LocalDateTime now = LocalDateTime.now();
    for (ProductPresaleEntity presale : presales) {
      if (isPresalePurchasable(presale, now)) {
        throw new com.moyuyo.common.exception.BusinessException(400, "该商品正在预售，请从预售入口下单");
      }
    }
  }

  /** 全款/定金预售当前是否处于可下单窗口 */
  private boolean isPresalePurchasable(ProductPresaleEntity presale, LocalDateTime now) {
    if (presale == null || PRESALE_TYPE_DISPLAY.equalsIgnoreCase(presale.getPresaleType())) {
      return false;
    }
    if ("DRAFT".equals(presale.getStatus()) || "ENDED".equals(presale.getStatus())) {
      return false;
    }
    if (presale.getStartTime() != null && now.isBefore(presale.getStartTime())) {
      return false;
    }
    if (presale.getEndTime() != null && now.isAfter(presale.getEndTime())) {
      return false;
    }
    return true;
  }

  /** 是否为定金预售订单（定金阶段支付后不发货） */
  private boolean isDepositPresale(OrderEntity order) {
    return order != null && order.isPresale() && PRESALE_STAGE_DEPOSIT.equals(order.getPresaleStage());
  }

  /** 每人限购校验：已购数量取该用户该预售下有效订单的商品数量之和（已取消/已退款不计入） */
  private void checkPresaleLimit(Long userId, ProductPresaleEntity presale, int quantity) {
    Integer limit = presale.getLimitPerUser();
    if (userId == null || limit == null || limit <= 0) {
      return;
    }
    List<OrderEntity> orders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getUserId, userId)
            .eq(OrderEntity::getPresaleId, presale.getId())
            .eq(OrderEntity::getOrderType, ORDER_TYPE_PRESALE)
            .eq(OrderEntity::getDeleteStatus, 0)
            // 已退款（如尾款逾期退定金）说明交易未成立，不再占用限购名额
            .notIn(OrderEntity::getStatus, CANCELLED.name(), REFUNDED.name())
            .in(OrderEntity::getPresaleStage, PRESALE_STAGE_FULL, PRESALE_STAGE_DEPOSIT));
    if (orders == null || orders.isEmpty()) {
      return;
    }
    List<Long> orderIds = orders.stream().map(OrderEntity::getId).collect(Collectors.toList());
    List<OrderItemEntity> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
            .in(OrderItemEntity::getOrderId, orderIds));
    int bought = items == null ? 0 : items.stream()
        .filter(it -> it.getQuantity() != null && it.getQuantity() > 0)
        .mapToInt(OrderItemEntity::getQuantity).sum();
    if (bought + quantity > limit) {
      throw new IllegalArgumentException("该预售商品每人限购 " + limit + " 件，您已购买 " + bought + " 件");
    }
  }

  /**
   * 原子占用预售库存。
   * 条件 UPDATE 保证并发下不会超卖；达到上限时同步把状态置为 SOLD_OUT。
   * 注意 MySQL 的 SET 子句从左到右求值，因此 status 必须写在 sold_count 之前。
   */
  private void reservePresaleStock(Long presaleId, int quantity, String productName) {
    LambdaUpdateWrapper<ProductPresaleEntity> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(ProductPresaleEntity::getId, presaleId)
        .apply("(stock_limit IS NULL OR sold_count + {0} <= stock_limit)", quantity)
        .setSql("status = IF(stock_limit IS NOT NULL AND sold_count + " + quantity
            + " >= stock_limit, 'SOLD_OUT', status), sold_count = sold_count + " + quantity);
    int affected = productPresaleMapper.update(null, wrapper);
    if (affected == 0) {
      throw new IllegalStateException("预售库存不足: " + productName);
    }
  }

  /** 取消预售订单时释放预售库存（尾款单不重复释放，定金已占用过库存） */
  private void releasePresaleStock(OrderEntity order) {
    if (order.getPresaleId() == null) {
      return;
    }
    List<OrderItemEntity> items = orderItemMapper.selectList(
        new LambdaQueryWrapper<OrderItemEntity>()
            .eq(OrderItemEntity::getOrderId, order.getId()));
    int quantity = items == null ? 0 : items.stream()
        .filter(it -> it.getQuantity() != null && it.getQuantity() > 0)
        .mapToInt(OrderItemEntity::getQuantity).sum();
    if (quantity <= 0) {
      return;
    }
    // status 先在旧 sold_count 上判断是否需要从 SOLD_OUT 恢复，再扣减 sold_count
    LambdaUpdateWrapper<ProductPresaleEntity> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(ProductPresaleEntity::getId, order.getPresaleId())
        .setSql("status = IF(status = 'SOLD_OUT' AND (end_time IS NULL OR end_time > NOW()), 'ONGOING', status), "
            + "sold_count = GREATEST(sold_count - " + quantity + ", 0)");
    productPresaleMapper.update(null, wrapper);
  }

  /** 生成订单号：ORD + yyyyMMdd + 8 位雪花 ID 后缀 */
  private String generateOrderNo() {
    String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String snowId = String.valueOf(IdWorker.getId());
    return "ORD" + datePart + snowId.substring(snowId.length() - 8);
  }

  /**
   * 按 addressId 快照收件人信息到订单。
   * 查询失败只记录日志，不阻断下单。
   */
  private void snapshotAddress(OrderEntity order, Long addressId, Long userId) {
    if (addressId == null) {
      return;
    }
    AddressEntity address;
    try {
      address = addressMapper.selectById(addressId);
    } catch (Exception e) {
      log.warn("[order] 创建订单快照地址失败，addressId={}, orderNo={}", addressId, order.getOrderNo(), e);
      return;
    }
    if (address == null) {
      return;
    }
    // 越权防护：只能使用本人收货地址，否则订单快照会泄露他人姓名/电话/详细地址
    if (userId != null && !userId.equals(address.getUserId())) {
      throw new IllegalArgumentException("收货地址不存在或无权使用");
    }
    order.setReceiverName(address.getReceiver());
    order.setReceiverPhone(address.getPhone());
    order.setReceiverAddress(formatFullAddress(address));
    order.setReceiverZip(address.getZipCode());
  }

  /**
   * 计算会员折扣金额：按会员等级统一折扣，作用于非秒杀商品的销售价。
   *
   * <p>规则：与优惠券叠加；秒杀商品不参与（以 mo_flash_sale 中当前时间生效的活动判定）。
   */
  private BigDecimal computeMemberDiscount(Long userId, List<OrderItemEntity> items) {
    if (userId == null || items == null || items.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal rate = memberPriceService.getDiscountForUser(userId);
    // 1.00 = 原价（无折扣），并防御非法系数
    if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
      return BigDecimal.ZERO;
    }

    // 一次性查询商品集合中正在进行的秒杀活动，避免逐条查询
    List<Long> productIds = items.stream()
        .map(OrderItemEntity::getProductId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    Set<Long> flashSaleProductIds = new HashSet<>();
    if (!productIds.isEmpty()) {
      LocalDateTime now = LocalDateTime.now();
      List<FlashSaleEntity> activeFlashSales = flashSaleMapper.selectList(
          new LambdaQueryWrapper<FlashSaleEntity>()
              .in(FlashSaleEntity::getProductId, productIds)
              .eq(FlashSaleEntity::getActive, true)
              .le(FlashSaleEntity::getStartTime, now)
              .ge(FlashSaleEntity::getEndTime, now));
      for (FlashSaleEntity flash : activeFlashSales) {
        if (flash.getProductId() != null) {
          flashSaleProductIds.add(flash.getProductId());
        }
      }
    }

    // 仅对非秒杀商品按销售价累计，折扣金额 = 可优惠金额 × (1 - 折扣系数)
    BigDecimal eligibleAmount = BigDecimal.ZERO;
    for (OrderItemEntity item : items) {
      if (item.getPrice() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
        continue;
      }
      if (item.getProductId() != null && flashSaleProductIds.contains(item.getProductId())) {
        continue;
      }
      // 营销活动商品不叠加会员折扣
      if (item.getCampaignId() != null) {
        continue;
      }
      eligibleAmount = eligibleAmount.add(
          item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
    if (eligibleAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal discount = eligibleAmount.subtract(eligibleAmount.multiply(rate))
        .setScale(2, java.math.RoundingMode.HALF_UP);
    return discount.max(BigDecimal.ZERO);
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
      // latest 在 line 283 之后已做 null-check；JDT 跨分支流分析弱，显式抛一次以消除 Potential null pointer access 警告
      payment.setAmount(Objects.requireNonNull(latest).getPayAmount());
      payment.setStatus("SUCCESS");
      payment.setPaidAt(java.time.LocalDateTime.now());
      paymentMapper.insert(payment);
    } catch (Exception e) {
      log.warn("[payment-reconcile] 落对账流水失败: orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
    }
  }

  @Override
  public IPage<OrderEntity> listOrders(Long userId, int page, int size, String status,
      String orderType, String excludeOrderType) {
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

    // 订单类型过滤：订阅 tab 只查 SUBSCRIPTION；待发货 tab 排除订阅单（订阅支付即开通，无发货动作）
    if (orderType != null && !orderType.isEmpty()) {
      wrapper.eq(OrderEntity::getOrderType, orderType);
    }
    if (excludeOrderType != null && !excludeOrderType.isEmpty()) {
      wrapper.ne(OrderEntity::getOrderType, excludeOrderType);
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
   * <p>
   * 实现为 public，供打印 / 燕文取号等入口直接复用（详见 {@link OrderService#fillAddressIfAbsent}）。
   */
  @Override
  public void fillAddressIfAbsent(OrderEntity order) {
    if (order == null) return;
    boolean hasSnapshot = isNotBlank(order.getReceiverName())
        || isNotBlank(order.getReceiverPhone())
        || isNotBlank(order.getReceiverAddress());
    if (hasSnapshot) return;
    if (order.getAddressId() == null) return;
    try {
      AddressEntity address = addressMapper.selectById(order.getAddressId());
      if (address == null) return;
      // 越权兜底：历史脏数据中若地址不属于下单用户，不回填，避免订单详情泄露他人收货信息
      if (order.getUserId() != null && !order.getUserId().equals(address.getUserId())) {
        return;
      }
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
    if (order.isPresale()) {
      // 预售订单未扣现货库存，只释放预售占用量；尾款单不重复释放（定金单已占用）
      if (!PRESALE_STAGE_BALANCE.equals(order.getPresaleStage())) {
        releasePresaleStock(order);
      }
    } else {
      restoreStockForOrderItems(orderId);
    }

    // 释放营销活动占用额度（活动总量/预算/每人限购）
    try {
      campaignPricingService.releaseUsage(orderId);
    } catch (Exception e) {
      log.warn("[order] 取消订单释放活动额度失败: orderId={}, reason={}", orderId, e.getMessage());
    }

    // 订单取消通知（用户主动取消与超时自动取消都会走这里）
    try {
      String content = "您的订单 " + order.getOrderNo() + " 已取消"
          + (reason == null || reason.isBlank() ? "" : "，原因：" + reason);
      notificationService.notifyUser(order.getUserId(), "ORDER", "ORDER",
          "订单已取消", content, order.getId());
    } catch (Exception e) {
      log.warn("[order] 取消通知发送失败: orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
    }

    // 取消未支付订单：释放本次下单"占用(LOCKED)"的优惠券，供用户重新使用。
    // releaseCoupon 内部条件更新(LOCKED & usedOrderId=orderId)，重复取消/并发取消命中 0 行不报错（幂等）。
    if (order.getUserCouponId() != null) {
      try {
        couponService.releaseCoupon(order.getUserCouponId(), orderId);
      } catch (Exception e) {
        log.warn("[order] 取消订单释放优惠券失败: orderId={}, userCouponId={}, reason={}",
                orderId, order.getUserCouponId(), e.getMessage());
      }
    }

    // 取消未支付订单：解冻本次下单"冻结"的抵扣积分，归还用户可用余额。
    // 解冻同样走条件更新(points_frozen >= points)，无对应冻结时安全跳过（幂等、不误还）；
    // 每单只解冻一次由上面的 PENDING_PAY→CANCELLED 原子抢占保证。
    if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
      try {
        memberService.unfreezePoints(order.getUserId(), order.getPointsUsed(),
            order.getOrderNo(), "订单取消解冻积分：" + order.getOrderNo());
      } catch (Exception e) {
        log.warn("[order] 取消订单解冻积分失败: orderId={}, pointsUsed={}, reason={}",
                orderId, order.getPointsUsed(), e.getMessage());
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
    // 定金单与订阅单支付后都停在"已支付"：定金单避免提前发货，订阅单无实物履约
    boolean depositOrder = isDepositPresale(order);
    String paidStatus = (depositOrder || order.isSubscription())
        ? PAID.name() : PENDING_SHIP.name();
    LocalDateTime paidAt = LocalDateTime.now();
    int claimed = orderMapper.update(null,
        new LambdaUpdateWrapper<OrderEntity>()
            .eq(OrderEntity::getId, order.getId())
            .eq(OrderEntity::getStatus, PENDING_PAY.name())
            .set(OrderEntity::getStatus, paidStatus)
            .set(OrderEntity::getPaidAt, paidAt)
            .set(OrderEntity::getPayChannel, payChannel)
            .set(OrderEntity::getPayTransactionId, transactionId));
    if (claimed == 0) {
      // 抢占失败：可能已被支付(重复回调)或已被取消/拦截，进入对账分支
      handlePaymentForNonPendingOrder(order, payChannel, transactionId);
      return;
    }
    // 同步内存对象，供下方任务/Woo/通知使用
    order.setStatus(paidStatus);
    order.setPaidAt(paidAt);
    order.setPayChannel(payChannel);
    order.setPayTransactionId(transactionId);

    // 营销活动用量转正式消耗（失败不阻断支付，记日志人工核对）
    try {
      campaignPricingService.confirmUsage(order.getId());
    } catch (Exception e) {
      log.error("[campaign] 支付成功确认活动用量失败 orderNo={}, reason={}",
          order.getOrderNo(), e.getMessage());
    }

    // 记录支付流水
    PaymentEntity payment = new PaymentEntity();
    payment.setOrderId(order.getId());
    payment.setPayChannel(payChannel);
    payment.setTransactionId(transactionId);
    payment.setAmount(order.getPayAmount());
    payment.setStatus("SUCCESS");
    payment.setPaidAt(LocalDateTime.now());
    paymentMapper.insert(payment);

    // 同步写入交易流水（mo_finance_record），供后台财务概览"交易流水"实时展示
    try {
      FinanceRecordEntity record = new FinanceRecordEntity();
      record.setOrderNo(order.getOrderNo());
      record.setType("PAYMENT");
      record.setChannel(payChannel);
      record.setAmount(order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO);
      record.setStatus("SUCCESS");
      record.setCreateTime(LocalDateTime.now());
      financeRecordMapper.insert(record);
    } catch (Exception e) {
      // 流水写入失败不影响支付主流程，记日志供后续对账
      log.error("[finance-record] 写入 PAYMENT 流水失败 orderNo={}, reason={}",
          orderNo, e.getMessage(), e);
    }

    log.info("支付回调处理成功: orderNo={}, transactionId={}", orderNo, transactionId);

    // 订阅开通：与支付回调同事务，开通失败则整体回滚，
    // 由支付渠道重投，避免"钱到账但订阅未开通"
    if (order.isSubscription()) {
      subscribeService.activateByOrder(order);
    }

    // 优惠券闭环：支付成功后才把"占用中(LOCKED)"的券真正核销为"已使用(USED)"。
    // 抢占式状态更新（PENDING_PAY→PENDING_SHIP）保证本段每个订单只执行一次；
    // 核销失败仅告警，不阻断支付主流程（钱已到账）。
    if (order.getUserCouponId() != null) {
      try {
        couponService.markCouponUsed(order.getUserCouponId(), order.getId());
      } catch (Exception e) {
        log.error("[coupon] 支付成功核销优惠券失败，请人工对账 orderNo={}, userCouponId={}, reason={}",
            order.getOrderNo(), order.getUserCouponId(), e.getMessage());
      }
    }

    // 积分抵扣闭环：支付成功把下单时"冻结"的抵扣积分转为"实扣"（同一个 UPDATE 内扣总积分与冻结额，
    // 绝不再额外 spendPoints，避免双扣）。余额/冻结不足等异常不阻断支付(钱已到账)，
    // 仅告警并交给对账/人工处理；抢占式状态更新保证本段只执行一次。
    if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
      try {
        boolean settled = memberService.consumeFrozenPoints(order.getUserId(), order.getPointsUsed(),
            order.getOrderNo(), "订单积分抵扣");
        if (settled) {
          log.info("订单积分冻结已转实扣: orderNo={}, pointsUsed={}", order.getOrderNo(), order.getPointsUsed());
        } else {
          log.error("[points] 支付成功积分实扣失败(冻结/余额不足或已处理)，请人工对账 orderNo={}, pointsUsed={}",
              order.getOrderNo(), order.getPointsUsed());
        }
      } catch (Exception e) {
        log.error("[points] 支付成功积分实扣异常，请人工对账 orderNo={}, pointsUsed={}, reason={}",
            order.getOrderNo(), order.getPointsUsed(), e.getMessage());
      }
    }

    // 订阅订单不计入购物任务与累计消费
    if (!order.isSubscription()) {
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
    }

    // 付款确认后实时同步到 WooCommerce
    // 受管理后台"WC 同步"开关控制：关闭时不推送，避免开关被支付回调绕过
    // syncOrderToWooCommerce 内部已有 try-catch，失败时仅记录 syncStatus=-1，不影响主流程
    try {
      // 定金单尚未履约、订阅单为虚拟服务，均不推送到 WooCommerce
      if (!depositOrder && !order.isSubscription() && wooCommerceSyncService.isOrderSyncEnabled()) {
        wooCommerceSyncService.syncOrderToWooCommerce(order);
      } else if (!depositOrder && !order.isSubscription()) {
        log.info("WooCommerce 订单同步开关已关闭，跳过推送: orderNo={}", orderNo);
      }
    } catch (Exception e) {
      // 兜底：即便 syncOrderToWooCommerce 内部异常也吞掉，不让支付流程回滚
      log.error("触发 WooCommerce 订单同步时异常: orderNo={}, reason={}",
              orderNo, e.getMessage());
    }

    // 发送订单支付成功通知（异步 + MQ 不可用时降级为同步落库）
    try {
      String orderTitle;
      String orderContent;
      if (depositOrder) {
        orderTitle = "预售定金支付成功";
        orderContent = "您的预售定金订单 " + order.getOrderNo() + " 已支付成功，请在尾款期内支付尾款";
      } else if (order.isSubscription()) {
        orderTitle = "订阅开通成功";
        orderContent = "您的订阅 " + order.getOrderNo() + " 已开通成功，可在订阅页查看有效期";
      } else {
        orderTitle = "订单支付成功";
        orderContent = "您的订单 " + order.getOrderNo() + " 已支付成功，商家将尽快为您发货";
      }
      try {
        // bizType=ORDER：APP 点击通知跳转订单详情（relatedId 为订单 ID）
        notificationMessageProducer.send(
                order.getUserId(), "ORDER", "ORDER", orderTitle, orderContent, order.getId());
      } catch (Exception mqEx) {
        // MQ 不可用降级：直接同步落库，保证用户侧能看到通知
        log.warn("MQ 发送失败，降级同步落库通知: orderNo={}, reason={}",
                orderNo, mqEx.getMessage());
        notificationService.saveNotification(
                order.getUserId(), "ORDER", "ORDER", orderTitle, orderContent, order.getId());
      }
    } catch (Exception e) {
      log.warn("[order] send notification failed: orderNo={}, reason={}", orderNo, e.getMessage());
    }

    // 订阅订单不参与消费返积分
    if (!order.isSubscription()) {
      // 消费返积分：1 USD = 10 积分，首单 2 倍；失败仅打日志不影响支付主流程
      try {
        pointsRewardService.rewardForOrder(order);
      } catch (Exception e) {
        log.warn("[order] grant reward points failed: orderNo={}, reason={}", orderNo, e.getMessage());
      }
    }

    // 订阅订单不计入被邀请人首单奖励
    if (!order.isSubscription()) {
      // 邀请奖励：被邀请人首单支付成功后双方各得 200 积分（内部幂等，重复回调不会重复发放）
      try {
        inviteService.markOrderedByInvitee(order.getUserId(), order.getId());
      } catch (Exception e) {
        log.warn("[order] grant invite reward failed: orderNo={}, reason={}", orderNo, e.getMessage());
      }
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
    // 原子抢占（SHIPPED → RECEIVED）：并发重复确认时只有一次成功，
    // 避免重复覆盖收货时间与重复发送收货通知
    LocalDateTime receivedTime = LocalDateTime.now();
    int claimed = orderMapper.update(null,
        new LambdaUpdateWrapper<OrderEntity>()
            .eq(OrderEntity::getId, orderId)
            .eq(OrderEntity::getStatus, SHIPPED.name())
            .set(OrderEntity::getStatus, RECEIVED.name())
            .set(OrderEntity::getReceivedTime, receivedTime));
    if (claimed == 0) {
      throw new IllegalStateException("当前订单状态不允许确认收货");
    }
    order.setStatus(RECEIVED.name());
    order.setReceivedTime(receivedTime);
    // 同步写入物流签收时间：物流页的 currentStatus 依赖 mo_logistics.received_at，
    // 只更新订单会导致"订单已收货、物流页仍显示运输中"，且轮询任务只扫 SHIPPED 订单，
    // 该物流记录的签收时间将永远补不上。
    logisticsMapper.update(null,
        new LambdaUpdateWrapper<LogisticsEntity>()
            .eq(LogisticsEntity::getOrderId, orderId)
            .isNull(LogisticsEntity::getReceivedAt)
            .set(LogisticsEntity::getReceivedAt, order.getReceivedTime()));

    // 确认收货通知
    try {
      notificationService.notifyUser(order.getUserId(), "ORDER", "ORDER",
          "订单已确认收货",
          "您的订单 " + order.getOrderNo() + " 已确认收货，感谢选购，期待您的评价",
          order.getId());
    } catch (Exception e) {
      log.warn("[order] 收货通知发送失败: orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
    }
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

  /**
   * 服务端权威运费计算：从地址查国家，按 (country, shippingMethod) 调用 ShippingRateService 重算，
   * 覆盖前端传入的 freight。
   * <p>
   * 回退策略（P0 修复后）：
   * <ol>
   *   <li>addressId 为空（兼容旧调用）/ 查不到地址 / country 为空 → 抛 BusinessException(400)，
   *       阻断下单（防止改包刷价 + 脏数据落库）</li>
   *   <li>zone 未命中（该国家不在可发货集合）或 method 未配置 → 抛 BusinessException(400)，
   *       阻断下单（核心防御：之前会沿用前端值，导致攻击者改 freight=0 即可免费购）</li>
   *   <li>ShippingRateService 抛异常（DB/Redis 故障）→ 抛 BusinessException(503)，
   *       告知用户稍后重试</li>
   * </ol>
   * <p>
   * 关键：服务端算不出运费时**绝不再信任前端值**——这是 P0 漏洞修复点。
   */
  private BigDecimal resolveServerFreight(Long userId, Long addressId, List<OrderItemEntity> items,
                                          BigDecimal goodsAmount, String shippingMethod,
                                          BigDecimal clientFreight) {
    if (addressId == null) {
      throw new com.moyuyo.common.exception.BusinessException(400, "请选择收货地址后再下单");
    }
    AddressEntity address;
    try {
      address = addressMapper.selectById(addressId);
    } catch (Exception e) {
      throw new com.moyuyo.common.exception.BusinessException(503,
          "运费计算服务暂不可用，请稍后重试", e);
    }
    if (address == null) {
      throw new com.moyuyo.common.exception.BusinessException(400, "收货地址不存在，请重新选择");
    }
    if (address.getCountry() == null || address.getCountry().isBlank()) {
      throw new com.moyuyo.common.exception.BusinessException(400, "收货地址缺少国家信息，请补全后再下单");
    }

    // 转换 items -> ShippingQuoteRequest.Item（含重量：商品 weight × quantity，无则按 500g 默认）
    // 运费类系数整单读取一次，避免每个商品都全表查询系统配置
    Map<String, BigDecimal> classMultipliers = readShippingClassMultipliers();
    List<com.moyuyo.common.dto.shipping.ShippingQuoteRequest.Item> quoteItems = new ArrayList<>(items.size());
    for (OrderItemEntity it : items) {
      com.moyuyo.common.dto.shipping.ShippingQuoteRequest.Item qi =
              new com.moyuyo.common.dto.shipping.ShippingQuoteRequest.Item();
      qi.setProductId(it.getProductId());
      qi.setSkuId(it.getSkuId());
      qi.setQuantity(it.getQuantity() == null ? 0 : it.getQuantity());
      Integer grams = lookupChargeableWeightGrams(it.getProductId(), classMultipliers);
      qi.setWeightGrams(grams);
      quoteItems.add(qi);
    }

    String method = shippingMethod == null || shippingMethod.isBlank() ? "standard" : shippingMethod;
    // 兼容旧订单：shippingMethod 为空时按 standard 处理；如果该 zone 没 standard 规则，
    // calcFreight 会返 null → 走 400 抛业务异常，不沿用前端值
    BigDecimal serverFreight;
    try {
      serverFreight = shippingRateService.calcFreight(
              address.getCountry(), method, quoteItems, goodsAmount);
    } catch (Exception e) {
      // DB/Redis 故障：503 让用户重试，不再沿用前端值
      log.error("[order] 服务端运费计算异常 country={} method={}", address.getCountry(), method, e);
      throw new com.moyuyo.common.exception.BusinessException(503,
          "运费计算服务暂不可用，请稍后重试", e);
    }
    if (serverFreight == null) {
      // P0 修复：不再沿用前端值。该 country 不在可发货 zone 或 method 未配置 → 阻断下单。
      log.warn("[order] 服务端运费不可用 country={} method={}, 阻断下单（不再沿用前端值以防改包）",
              address.getCountry(), method);
      throw new com.moyuyo.common.exception.BusinessException(400,
          "当前收货地址或配送方式不可用，请更换地址或选择其他配送方式");
    }

    // 防御：若前后端运费偏差超过 10%，记告警日志（运营可在管理后台核查）
    BigDecimal fallback = clientFreight == null ? BigDecimal.ZERO : clientFreight;
    if (fallback.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal diff = serverFreight.subtract(fallback).abs();
      // divide 必须带 RoundingMode，否则 result 是无限循环小数时会抛 ArithmeticException
      BigDecimal ratio = diff.divide(fallback.max(BigDecimal.valueOf(0.01)), 4, java.math.RoundingMode.HALF_UP);
      if (ratio.compareTo(new BigDecimal("0.10")) > 0) {
        log.warn("[order] 运费差异超过 10% client={} server={} country={} method={}",
                fallback, serverFreight, address.getCountry(), method);
      }
    }
    return serverFreight;
  }

  /**
   * 判断订单是否全部为虚拟商品（全部 is_virtual=true 时不产生运费）。
   */
  private boolean isAllVirtual(List<OrderItemEntity> items, Map<Long, ProductEntity> productMap) {
    if (items == null || items.isEmpty()) {
      return false;
    }
    for (OrderItemEntity item : items) {
      ProductEntity product = productMap.get(item.getProductId());
      Map<String, Object> attrs = ProductAttributes.parse(product == null ? null : product.getAttributes());
      if (!ProductAttributes.bool(attrs, "is_virtual", false)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 计算订单税费。
   * <p>
   * 税率来自系统配置 product_tax_rate（百分比，默认 0 = 不计税）：
   * - tax_status=none      不计税
   * - tax_status=shipping  仅对运费计税
   * - tax_status=taxable   按商品金额计税
   * - tax_class=zero-rate  税率为 0；reduced-rate 按标准税率的一半
   * <p>
   * 税基按优惠后金额计算：商品级优惠按应税商品金额占比分摊后扣减，运费优惠不参与分摊。
   */
  private BigDecimal computeTaxAmount(List<OrderItemEntity> items, BigDecimal freight,
                                      Map<Long, ProductEntity> productMap,
                                      BigDecimal goodsAmount, BigDecimal goodsDiscount) {
    BigDecimal ratePercent = readProductTaxRatePercent();
    if (ratePercent.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal rate = ratePercent.divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
    BigDecimal taxableGoods = BigDecimal.ZERO;
    boolean taxShipping = false;
    for (OrderItemEntity item : items) {
      ProductEntity product = productMap.get(item.getProductId());
      Map<String, Object> attrs = ProductAttributes.parse(product == null ? null : product.getAttributes());
      String taxStatus = ProductAttributes.str(attrs, "tax_status", "taxable");
      if ("none".equalsIgnoreCase(taxStatus)) {
        continue;
      }
      if ("shipping".equalsIgnoreCase(taxStatus)) {
        taxShipping = true;
        continue;
      }
      String taxClass = ProductAttributes.str(attrs, "tax_class", "standard");
      BigDecimal classMultiplier = BigDecimal.ONE;
      if ("zero-rate".equalsIgnoreCase(taxClass)) {
        classMultiplier = BigDecimal.ZERO;
      } else if ("reduced-rate".equalsIgnoreCase(taxClass)) {
        classMultiplier = new BigDecimal("0.5");
      }
      if (item.getPrice() == null || classMultiplier.compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }
      BigDecimal lineAmount = item.getPrice()
          .multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity()));
      taxableGoods = taxableGoods.add(lineAmount.multiply(classMultiplier));
    }

    BigDecimal taxableBase = taxableGoods;
    // 优惠按应税商品占比分摊（优惠后金额计税）
    if (taxableGoods.compareTo(BigDecimal.ZERO) > 0
        && goodsAmount != null && goodsAmount.compareTo(BigDecimal.ZERO) > 0
        && goodsDiscount != null && goodsDiscount.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal ratio = taxableGoods.divide(goodsAmount, 6, java.math.RoundingMode.HALF_UP);
      BigDecimal discountShare = goodsDiscount.min(goodsAmount)
          .multiply(ratio)
          .setScale(2, java.math.RoundingMode.HALF_UP);
      taxableBase = taxableGoods.subtract(discountShare).max(BigDecimal.ZERO);
    }
    if (taxShipping && freight != null) {
      taxableBase = taxableBase.add(freight);
    }
    return taxableBase.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
  }

  /** 读取系统配置中的商品税率（百分比），未配置或非法时按 0 处理 */
  private BigDecimal readProductTaxRatePercent() {
    try {
      Map<String, Object> config = systemConfigService.getConfig("order");
      Object value = config == null ? null : config.get("product_tax_rate");
      if (value == null || String.valueOf(value).isBlank()) {
        return BigDecimal.ZERO;
      }
      return new BigDecimal(String.valueOf(value).trim());
    } catch (Exception e) {
      log.warn("[order] 读取商品税率失败，按 0 处理: {}", e.getMessage());
      return BigDecimal.ZERO;
    }
  }

  /**
   * 读取运费类系数（可选配置 shipping_class_multiplier，JSON 形如 {"cold":1.2,"bulky":2}）。
   * 未配置时返回空 Map，统一按 1 倍处理，不影响现有运费。
   */
  private Map<String, BigDecimal> readShippingClassMultipliers() {
    Map<String, BigDecimal> result = new java.util.HashMap<>();
    try {
      Map<String, Object> config = systemConfigService.getConfig("shipping");
      Object raw = config == null ? null : config.get("shipping_class_multiplier");
      if (raw == null || String.valueOf(raw).isBlank()) {
        return result;
      }
      for (Map.Entry<String, Object> entry : ProductAttributes.parse(String.valueOf(raw)).entrySet()) {
        BigDecimal value = toDecimal(entry.getValue());
        if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
          result.put(entry.getKey(), value);
        }
      }
    } catch (Exception e) {
      log.warn("[order] 读取运费类系数失败，按 1 倍处理: {}", e.getMessage());
    }
    return result;
  }

  /**
   * 查询商品计费重量（克）：取「实际重量」与「体积重」较大值，再乘以运费类系数。
   * <p>
   * 体积重按行业通用公式 长×宽×高(cm³)/5000(kg)，换算为克即 /5；尺寸或重量缺失时按可用值处理，
   * 全部缺失返回 null 由 ShippingRateService 兜底 500g。
   */
  private Integer lookupChargeableWeightGrams(Long productId, Map<String, BigDecimal> classMultipliers) {
    try {
      ProductEntity p = productMapper.selectById(productId);
      if (p == null) {
        return null;
      }
      Integer grams = p.getWeight() == null ? null
          : p.getWeight().setScale(0, java.math.RoundingMode.CEILING).intValue();
      Map<String, Object> attrs = ProductAttributes.parse(p.getAttributes());
      Object dimensions = attrs.get("dimensions");
      if (dimensions instanceof Map<?, ?> dims) {
        BigDecimal length = toDecimal(dims.get("length"));
        BigDecimal width = toDecimal(dims.get("width"));
        BigDecimal height = toDecimal(dims.get("height"));
        if (length != null && width != null && height != null
            && length.compareTo(BigDecimal.ZERO) > 0
            && width.compareTo(BigDecimal.ZERO) > 0
            && height.compareTo(BigDecimal.ZERO) > 0) {
          int volumeGrams = length.multiply(width).multiply(height)
              .divide(BigDecimal.valueOf(5), 0, java.math.RoundingMode.CEILING).intValue();
          grams = grams == null ? volumeGrams : Math.max(grams, volumeGrams);
        }
      }
      String shippingClass = ProductAttributes.str(attrs, "shipping_class", "");
      BigDecimal multiplier = shippingClass.isEmpty() ? BigDecimal.ONE
          : classMultipliers.getOrDefault(shippingClass, BigDecimal.ONE);
      if (grams != null && multiplier.compareTo(BigDecimal.ONE) != 0) {
        grams = BigDecimal.valueOf(grams).multiply(multiplier)
            .setScale(0, java.math.RoundingMode.CEILING).intValue();
      }
      return grams;
    } catch (Exception e) {
      log.debug("[order] 查商品计费重量异常 productId={}", productId);
      return null;
    }
  }

  /** 安全地把配置/JSON 中的数字转为 BigDecimal，非法返回 null */
  private BigDecimal toDecimal(Object value) {
    if (value == null) {
      return null;
    }
    try {
      String text = String.valueOf(value).trim();
      return text.isEmpty() ? null : new BigDecimal(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

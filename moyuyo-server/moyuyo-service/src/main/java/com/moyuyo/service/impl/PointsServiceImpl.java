package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.entity.AddressEntity;
import com.moyuyo.dao.entity.CheckinMakeupEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.PointsExchangeEntity;
import com.moyuyo.dao.entity.PointsGoodsEntity;
import com.moyuyo.dao.entity.PointsLogEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.CheckinMakeupMapper;
import com.moyuyo.dao.mapper.PointsExchangeMapper;
import com.moyuyo.dao.mapper.PointsLogMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AddressService;
import com.moyuyo.service.MemberService;
import com.moyuyo.service.OrderService;
import com.moyuyo.service.PointsService;
import com.moyuyo.service.config.MembershipRuleConfigService;
import com.moyuyo.service.support.ProductAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

  private final PointsExchangeMapper pointsExchangeMapper;
  private final CheckinMakeupMapper checkinMakeupMapper;
  /** 积分商品复用主商品表：列表查询与兑换校验都走 mo_product */
  private final ProductMapper productMapper;
  /** 变体商品的规格列表，兑换时用于选择具体 SKU（后台按 SKU 发货） */
  private final ProductSkuMapper productSkuMapper;
  /** 兑换成功后生成 0 元积分订单，复用订单发货/物流/售后流程 */
  private final OrderService orderService;
  private final AddressService addressService;
  /** 补签次数/消耗积分、签到积分从会员规则配置读取（后台可调） */
  private final MembershipRuleConfigService ruleConfigService;
  private final UserMapper userMapper;
  private final PointsLogMapper pointsLogMapper;
  private final MemberService memberService;

  @Override
  public List<PointsGoodsEntity> listGoods(String category) {
    // 积分商品复用主商品表：只返回已开启积分兑换、配置了积分价且在售的商品
    LambdaQueryWrapper<ProductEntity> q = new LambdaQueryWrapper<ProductEntity>()
        .eq(ProductEntity::getAllowPointsExchange, true)
        .gt(ProductEntity::getPointsPrice, 0)
        .eq(ProductEntity::getOnSale, true)
        .orderByDesc(ProductEntity::getUpdateTime);
    if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
      try {
        // 前端传主商品分类 ID 时按分类过滤
        q.eq(ProductEntity::getCategoryId, Long.valueOf(category.trim()));
      } catch (NumberFormatException e) {
        // 旧的字符串分类（DIGITAL/COUPON）已不再支持，返回空集合避免全量返回
        return List.of();
      }
    }
    List<PointsGoodsEntity> list = productMapper.selectList(q).stream()
        .map(this::toPointsGoodsView)
        .collect(Collectors.toList());
    // 附带 SKU 列表，APP 兑换变体商品时用于选择规格
    fillSkus(list);
    return list;
  }

  /**
   * 批量填充商品的 SKU 列表（一次 IN 查询，避免列表逐条查询造成 N+1）。
   */
  private void fillSkus(List<PointsGoodsEntity> goodsList) {
    if (goodsList.isEmpty()) {
      return;
    }
    List<Long> productIds = goodsList.stream()
        .map(PointsGoodsEntity::getId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    if (productIds.isEmpty()) {
      return;
    }
    Map<Long, List<ProductSkuEntity>> skuMap = productSkuMapper.selectList(
            new LambdaQueryWrapper<ProductSkuEntity>()
                .in(ProductSkuEntity::getProductId, productIds)
                .orderByAsc(ProductSkuEntity::getId))
        .stream()
        .collect(Collectors.groupingBy(ProductSkuEntity::getProductId));
    for (PointsGoodsEntity goods : goodsList) {
      goods.setSkus(skuMap.getOrDefault(goods.getId(), List.of()));
    }
  }

  /**
   * 主商品 → 积分商品视图。
   * 字段名沿用 APP 端既有约定（id/name/image/points/stock/needAddress），id 传商品 ID，
   * 这样积分商城页面无需理解商品表结构。
   */
  private PointsGoodsEntity toPointsGoodsView(ProductEntity product) {
    PointsGoodsEntity view = new PointsGoodsEntity();
    view.setId(product.getId());
    view.setName(product.getName());
    view.setDescription(product.getShortDetail() != null ? product.getShortDetail() : product.getTags());
    view.setImage(product.getMainImage());
    view.setCategory(product.getCategoryId() == null ? null : String.valueOf(product.getCategoryId()));
    view.setPoints(product.getPointsPrice());
    // -1 表示不限库存，前端按"不限"展示
    view.setStock(product.getPointsStock() == null ? 0 : product.getPointsStock());
    view.setTotalExchanged(0);
    // 虚拟商品（券类）兑换不需要收货地址
    Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
    view.setNeedAddress(!ProductAttributes.bool(attrs, "is_virtual", false));
    view.setStatus(Boolean.TRUE.equals(product.getOnSale()) ? 1 : 0);
    view.setSortOrder(0);
    return view;
  }

  /**
   * 章节 3.2：兑换礼品。
   * 1. 校验商品已开启积分兑换、上架且配置了积分价
   * 2. 校验积分余额
   * 3. 实物商品必须有收货地址（优先使用传入地址，其次用户默认地址）
   * 4. 扣减积分（写 EXCHANGE 流水）
   * 5. 扣减积分专属库存 + 生成 0 元积分订单（已支付待发货）
   * 6. 写兑换记录并关联商品与订单
   */
  @Override
  @Transactional
  public PointsExchangeEntity exchange(Long userId, Long goodsId, Long skuId, Long addressId,
      String receiverName, String receiverPhone, String receiverAddress) {
    ProductEntity product = productMapper.selectById(goodsId);
    if (product == null || !Boolean.TRUE.equals(product.getAllowPointsExchange())
        || product.getPointsPrice() == null || product.getPointsPrice() <= 0) {
      throw new IllegalArgumentException("礼品不存在或已下架");
    }
    if (product.getOnSale() == null || !product.getOnSale()) {
      throw new IllegalArgumentException("礼品不存在或已下架");
    }
    // 变体商品必须指定 SKU：后台按 SKU 发货，订单明细不能缺失规格
    ProductSkuEntity sku = resolveExchangeSku(goodsId, skuId);
    int cost = product.getPointsPrice();
    int balance = memberService.getPointsBalance(userId);
    if (balance < cost) {
      throw new IllegalStateException("积分不足，无法兑换");
    }

    // 实物商品需要收货地址：优先使用指定地址，未指定时回退到用户默认地址
    Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
    boolean needAddr = !ProductAttributes.bool(attrs, "is_virtual", false);
    AddressEntity address = addressId != null ? addressService.getById(addressId, userId) : null;
    if (address == null) {
      address = findDefaultAddress(userId);
    }
    if (needAddr && address == null) {
      throw new IllegalArgumentException("请先添加收货地址后再兑换实物礼品");
    }

    // 扣减积分（原子条件更新：可用积分不足时直接失败，避免并发下两笔兑换都通过余额校验）
    boolean spent = memberService.spendAvailablePoints(
        userId, cost, "EXCHANGE", String.valueOf(goodsId), "兑换礼品：" + product.getName());
    if (!spent) {
      throw new IllegalStateException("积分不足，无法兑换");
    }

    // 扣减积分专属库存 + 生成 0 元订单（已支付待发货），发货/售后复用订单流程
    OrderEntity order = orderService.createPointsExchangeOrder(
        userId, goodsId, 1, sku == null ? null : sku.getId(),
        address == null ? null : address.getId(), cost);

    // 写兑换记录并关联商品与订单，便于后台按兑换单追溯
    PointsExchangeEntity exchange = new PointsExchangeEntity();
    exchange.setUserId(userId);
    exchange.setGoodsId(goodsId);
    exchange.setProductId(goodsId);
    exchange.setOrderId(order.getId());
    exchange.setGoodsName(product.getName());
    exchange.setPointsCost(cost);
    if (address != null) {
      exchange.setReceiverName(address.getReceiver());
      exchange.setReceiverPhone(address.getPhone());
      exchange.setReceiverAddress(formatAddress(address));
    } else {
      // 虚拟商品无地址时仍保留前端传入的联系信息（历史接口兼容）
      exchange.setReceiverName(receiverName);
      exchange.setReceiverPhone(receiverPhone);
      exchange.setReceiverAddress(receiverAddress);
    }
    exchange.setStatus("PENDING");
    pointsExchangeMapper.insert(exchange);

    log.info("Points exchange: userId={}, productId={}, cost={}, orderId={}",
        userId, goodsId, cost, order.getId());
    return exchange;
  }

  /**
   * 解析兑换规格：
   * - 无 SKU 的简单商品返回 null；
   * - 有 SKU 的变体商品必须传入合法 skuId（后台按 SKU 拣货发货）。
   */
  private ProductSkuEntity resolveExchangeSku(Long productId, Long skuId) {
    List<ProductSkuEntity> skus = productSkuMapper.selectList(
        new LambdaQueryWrapper<ProductSkuEntity>()
            .eq(ProductSkuEntity::getProductId, productId));
    if (skus.isEmpty()) {
      return null;
    }
    if (skuId == null) {
      throw new IllegalArgumentException("请选择商品规格");
    }
    return skus.stream()
        .filter(s -> skuId.equals(s.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("商品规格不存在"));
  }

  /** 取用户默认收货地址：listByUserId 已按 isDefault 降序，首条即默认地址 */
  private AddressEntity findDefaultAddress(Long userId) {
    List<AddressEntity> list = addressService.listByUserId(userId);
    return list == null || list.isEmpty() ? null : list.get(0);
  }

  /** 拼接完整收货地址，口径与订单快照保持一致 */
  private String formatAddress(AddressEntity address) {
    return java.util.stream.Stream.of(address.getCountry(), address.getProvince(),
            address.getCity(), address.getDistrict(), address.getDetail())
        .filter(part -> part != null && !part.isBlank())
        .collect(Collectors.joining(" "));
  }

  @Override
  public List<PointsExchangeEntity> listMyExchanges(Long userId) {
    return pointsExchangeMapper.selectList(
        new LambdaQueryWrapper<PointsExchangeEntity>()
            .eq(PointsExchangeEntity::getUserId, userId)
            .orderByDesc(PointsExchangeEntity::getCreateTime));
  }

  /**
   * 章节 2.1：漏签补签规则
   * - 只能补"当月、今天之前"且当天没有签到流水的漏签日；date 为空时自动取当月最近的漏签日
   * - 每月可免费补签 1 次，之后每次补签消耗 50 积分
   * - 补签成功后写入 CHECKIN 流水，时间回填到被补签那一天（而不是操作当天），
   *   这样才真正修复连续签到：操作当天仍可正常签到，连续天数也能接上
   * - 补签不推进任务中心进度：被补日期可能落在上一周，推进"累计签到 5 天"会造成跨周口径混乱
   */
  @Override
  @Transactional
  public Map<String, Object> makeupCheckin(Long userId, LocalDate date) {
    LocalDate today = LocalDate.now();
    LocalDate target = date != null ? date : latestMissedDay(userId, today);

    if (target == null) {
      throw new IllegalStateException("当月没有可补签的漏签日");
    }
    if (!target.isBefore(today)) {
      throw new IllegalStateException("只能补签今天之前的日期，今天请直接签到");
    }
    if (target.getYear() != today.getYear() || target.getMonthValue() != today.getMonthValue()) {
      throw new IllegalStateException("只能补签当月的漏签日期");
    }
    // 取 [target, today] 窗口的签到日期集合，用于判断该日期是否已签到
    int span = (int) (today.toEpochDay() - target.toEpochDay()) + 1;
    if (memberService.getRecentCheckinDates(userId, span).contains(target)) {
      throw new IllegalStateException("该日期已签到，无需补签");
    }

    String ym = today.format(YM);

    CheckinMakeupEntity record = checkinMakeupMapper.selectOne(
        new LambdaQueryWrapper<CheckinMakeupEntity>()
            .eq(CheckinMakeupEntity::getUserId, userId)
            .eq(CheckinMakeupEntity::getYmonth, ym));

    int currentCount = record == null ? 0 : (record.getCount() == null ? 0 : record.getCount());
    MembershipRuleConfigService.MembershipRuleConfig rule = ruleConfigService.getConfig();
    boolean free = currentCount < rule.makeupFreePerMonth();
    int cost = free ? 0 : rule.makeupPointsCost();

    if (cost > 0) {
      int balance = memberService.getPointsBalance(userId);
      if (balance < cost) {
        throw new IllegalStateException("积分不足，无法补签");
      }
      memberService.spendPoints(userId, cost, ym, "漏签补签消耗积分");
    }

    // 写流水（CHECKIN +签到积分），created_at 回填到被补签那天（沿用当前时刻，便于流水列表展示）
    memberService.addPointsAt(userId, rule.checkinPoints(), "CHECKIN", "makeup:" + target,
        "漏签补签 +" + rule.checkinPoints() + " 积分（" + target + "）",
        target.atTime(LocalTime.now()));

    // 累加当月补签次数
    if (record == null) {
      CheckinMakeupEntity newRec = new CheckinMakeupEntity();
      newRec.setUserId(userId);
      newRec.setYmonth(ym);
      newRec.setCount(1);
      checkinMakeupMapper.insert(newRec);
    } else {
      record.setCount(currentCount + 1);
      checkinMakeupMapper.updateById(record);
    }

    Map<String, Object> result = new HashMap<>();
    result.put("points", 5);
    result.put("cost", cost);
    result.put("free", free);
    result.put("makeupDate", target.toString());
    result.put("monthCount", currentCount + 1);
    log.info("Checkin makeup: userId={}, date={}, month={}, free={}, cost={}",
        userId, target, ym, free, cost);
    return result;
  }

  /**
   * 章节 2.1：签到日历数据（指定月份的已签到日期 + 当前连续签到天数）。
   * 前端直接按 dates 渲染日历，不再用"最近 N 条积分流水"自行推算。
   */
  @Override
  public Map<String, Object> getCheckinCalendar(Long userId, YearMonth month) {
    LocalDate today = LocalDate.now();
    YearMonth target = month != null ? month : YearMonth.from(today);
    List<LocalDate> dates = memberService.getCheckinDatesOfMonth(userId, target);

    Map<String, Object> result = new HashMap<>();
    result.put("month", target.toString());
    result.put("dates", dates.stream().map(LocalDate::toString).collect(Collectors.toList()));
    result.put("checkedToday", dates.contains(today));
    result.put("streak", memberService.getCurrentCheckinStreak(userId));
    return result;
  }

  /**
   * 取当月最近的一个漏签日（今天之前、且当天没有签到流水），没有则返回 null。
   * 只回溯到当月 1 号，与"每月免费 1 次"的计数口径保持一致。
   */
  private LocalDate latestMissedDay(Long userId, LocalDate today) {
    // days=今天是几号 → 窗口正好是 [当月 1 号, 今天]
    Set<LocalDate> checkinDates = memberService.getRecentCheckinDates(userId, today.getDayOfMonth());
    LocalDate firstOfMonth = today.withDayOfMonth(1);
    LocalDate cursor = today.minusDays(1);
    while (!cursor.isBefore(firstOfMonth)) {
      if (!checkinDates.contains(cursor)) {
        return cursor;
      }
      cursor = cursor.minusDays(1);
    }
    return null;
  }
}
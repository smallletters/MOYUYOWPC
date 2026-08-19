package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.campaign.*;
import com.moyuyo.dao.admin.entity.AbTestEntity;
import com.moyuyo.dao.admin.entity.MarketingCampaignEntity;
import com.moyuyo.dao.admin.mapper.AbTestMapper;
import com.moyuyo.dao.admin.mapper.MarketingCampaignMapper;
import com.moyuyo.dao.entity.CouponEntity;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.FlashSaleOrderEntity;
import com.moyuyo.dao.entity.InviteEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.UserCouponEntity;
import com.moyuyo.dao.mapper.CouponMapper;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.FlashSaleOrderMapper;
import com.moyuyo.dao.mapper.InviteMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.UserCouponMapper;
import com.moyuyo.service.admin.CampaignMarketingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.moyuyo.common.enums.OrderStatusEnum.COMPLETED;

/**
 * 营销活动服务实现
 */
@Service
@RequiredArgsConstructor
public class CampaignMarketingServiceImpl implements CampaignMarketingService {

  private final MarketingCampaignMapper marketingCampaignMapper;
  private final AbTestMapper abTestMapper;
  private final OrderMapper orderMapper;
  private final CouponMapper couponMapper;
  private final UserCouponMapper userCouponMapper;
  private final FlashSaleMapper flashSaleMapper;
  private final FlashSaleOrderMapper flashSaleOrderMapper;
  private final InviteMapper inviteMapper;

  /** 日期格式化器(yyyy-MM-dd) */
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @Override
  public PageResponse<CampaignResponse> listCampaigns(int page, int size) {
    IPage<MarketingCampaignEntity> pageResult = marketingCampaignMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<MarketingCampaignEntity>()
            .orderByDesc(MarketingCampaignEntity::getCreateTime));

    List<CampaignResponse> records = pageResult.getRecords().stream()
        .map(this::toResponse)
        .collect(Collectors.toList());

    PageResponse<CampaignResponse> result = new PageResponse<>();
    result.setRecords(records);
    result.setTotal(pageResult.getTotal());
    result.setPage((int) pageResult.getCurrent());
    result.setSize((int) pageResult.getSize());
    return result;
  }

  /** Entity → Response VO */
  private CampaignResponse toResponse(MarketingCampaignEntity c) {
    CampaignResponse vo = new CampaignResponse();
    vo.setId(c.getId());
    vo.setName(c.getName());
    vo.setType(c.getType());
    vo.setStatus(c.getStatus());
    vo.setDescription(c.getDescription());
    vo.setStartDate(c.getStartDate() != null ? c.getStartDate().format(DATE_FMT) : null);
    vo.setEndDate(c.getEndDate() != null ? c.getEndDate().format(DATE_FMT) : null);
    vo.setParticipants(c.getParticipants());
    vo.setGmv(c.getGmv());
    vo.setBudget(c.getBudget());
    vo.setCost(c.getCost());
    vo.setCreateTime(c.getCreateTime());
    return vo;
  }

  @Override
  public OperationResult createCampaign(CampaignRequest request) {
    MarketingCampaignEntity entity = new MarketingCampaignEntity();
    entity.setName(request.getName() != null && !request.getName().isEmpty()
        ? request.getName() : "营销活动_" + System.currentTimeMillis());
    entity.setType(request.getType() != null && !request.getType().isEmpty()
        ? request.getType() : "DISCOUNT");
    entity.setDescription(request.getDescription());

    // 解析开始/结束时间,空值兜底为现在/30天后
    entity.setStartDate(request.getStartDate() != null
        ? parseDateTime(request.getStartDate()) : LocalDateTime.now());
    entity.setEndDate(request.getEndDate() != null
        ? parseDateTime(request.getEndDate()) : LocalDateTime.now().plusDays(30));

    entity.setBudget(request.getBudget() != null ? request.getBudget() : BigDecimal.ZERO);
    // 新建活动根据时间自动计算状态
    entity.setStatus(calculateStatus(entity.getStartDate(), entity.getEndDate()));
    marketingCampaignMapper.insert(entity);

    OperationResult result = new OperationResult();
    result.setId(entity.getId());
    result.setMessage("活动创建成功");
    return result;
  }

  @Override
  public OperationResult saveDraft(CampaignRequest request) {
    // 草稿允许最小信息,name 兜底为 "未命名草稿_" + 时间戳,确保落库后能区分
    MarketingCampaignEntity entity = new MarketingCampaignEntity();
    entity.setName(request.getName() != null && !request.getName().isEmpty()
        ? request.getName() : "未命名草稿_" + System.currentTimeMillis());
    entity.setType(request.getType() != null && !request.getType().isEmpty()
        ? request.getType() : "DISCOUNT");
    entity.setDescription(request.getDescription());

    // 草稿不强制要求时间,缺失时兜底为当前时刻,确保 INSERT 不会因 null 失败
    entity.setStartDate(request.getStartDate() != null
        ? parseDateTime(request.getStartDate()) : LocalDateTime.now());
    entity.setEndDate(request.getEndDate() != null
        ? parseDateTime(request.getEndDate()) : LocalDateTime.now().plusDays(30));

    entity.setBudget(request.getBudget() != null ? request.getBudget() : BigDecimal.ZERO);
    // 强制落 DRAFT,忽略 calculateStatus(),让草稿明确可见
    entity.setStatus("DRAFT");
    marketingCampaignMapper.insert(entity);

    OperationResult result = new OperationResult();
    result.setId(entity.getId());
    result.setMessage("草稿保存成功");
    return result;
  }

  @Override
  public CampaignDetailResponse getCampaignDetail(Long id) {
    MarketingCampaignEntity entity = marketingCampaignMapper.selectById(id);
    if (entity == null) {
      return null;
    }

    CampaignDetailResponse resp = new CampaignDetailResponse();
    resp.setCampaign(toResponse(entity));

    // 查询已完成订单统计(用于计算客单价)
    LambdaQueryWrapper<OrderEntity> orderWrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getStatus, COMPLETED.name());
    Long totalOrders = orderMapper.selectCount(orderWrapper);
    BigDecimal totalPayAmount = orderMapper.selectList(orderWrapper).stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal avgOrderValue = totalOrders > 0
        ? totalPayAmount.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    CampaignDetailResponse.Effects effects = new CampaignDetailResponse.Effects();
    effects.setOrderIncrease(0);
    effects.setConversionIncrease(0);
    effects.setAvgOrderValue(avgOrderValue);
    // ROI = gmv / budget,budget 为 0 时返回 0
    if (entity.getGmv() != null && entity.getBudget() != null
        && entity.getBudget().compareTo(BigDecimal.ZERO) > 0) {
      effects.setRoi(entity.getGmv().divide(entity.getBudget(), 2, RoundingMode.HALF_UP));
    } else {
      effects.setRoi(0);
    }
    resp.setEffects(effects);
    return resp;
  }

  @Override
  public OperationResult updateCampaign(Long id, CampaignRequest request) {
    MarketingCampaignEntity entity = marketingCampaignMapper.selectById(id);
    if (entity == null) {
      OperationResult result = new OperationResult();
      result.setMessage("活动不存在");
      return result;
    }

    // 仅更新非空字段(与原 Map 逻辑保持一致)
    if (request.getName() != null) entity.setName(request.getName());
    if (request.getType() != null) entity.setType(request.getType());
    if (request.getStatus() != null) entity.setStatus(request.getStatus());
    if (request.getDescription() != null) entity.setDescription(request.getDescription());
    if (request.getStartDate() != null) entity.setStartDate(parseDateTime(request.getStartDate()));
    if (request.getEndDate() != null) entity.setEndDate(parseDateTime(request.getEndDate()));
    if (request.getBudget() != null) entity.setBudget(request.getBudget());
    marketingCampaignMapper.updateById(entity);

    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("活动更新成功");
    return result;
  }

  @Override
  public OperationResult deleteCampaign(Long id) {
    MarketingCampaignEntity entity = marketingCampaignMapper.selectById(id);
    if (entity == null) {
      OperationResult result = new OperationResult();
      result.setMessage("活动不存在");
      return result;
    }
    marketingCampaignMapper.deleteById(id);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("活动删除成功");
    return result;
  }

  @Override
  public List<AbTestResponse> listAbTests() {
    List<AbTestEntity> records = abTestMapper.selectList(
        new LambdaQueryWrapper<AbTestEntity>().orderByDesc(AbTestEntity::getCreateTime));
    return records.stream().map(this::toAbTestResponse).collect(Collectors.toList());
  }

  /** AbTestEntity → AbTestResponse VO */
  private AbTestResponse toAbTestResponse(AbTestEntity t) {
    AbTestResponse vo = new AbTestResponse();
    vo.setId(t.getId());
    vo.setName(t.getName());
    vo.setStatus(t.getStatus());
    vo.setDescription(t.getDescription());
    vo.setGroupAVisitors(t.getGroupAVisitors());
    vo.setGroupBVisitors(t.getGroupBVisitors());
    vo.setGroupAConvRate(t.getGroupAConvRate());
    vo.setGroupBConvRate(t.getGroupBConvRate());
    vo.setStartTime(t.getStartTime());
    return vo;
  }

  @Override
  public OperationResult createAbTest(AbTestRequest request) {
    AbTestEntity entity = new AbTestEntity();
    entity.setName(request.getName());
    entity.setStatus(request.getStatus());
    entity.setDescription(request.getDescription());
    entity.setGroupAVisitors(request.getGroupAVisitors());
    entity.setGroupBVisitors(request.getGroupBVisitors());
    entity.setGroupAConvRate(request.getGroupAConvRate());
    entity.setGroupBConvRate(request.getGroupBConvRate());
    entity.setStartTime(LocalDateTime.now());
    abTestMapper.insert(entity);

    OperationResult result = new OperationResult();
    result.setId(entity.getId());
    result.setMessage("A/B测试创建成功");
    return result;
  }

  @Override
  public OperationResult updateAbTest(Long id, AbTestRequest request) {
    AbTestEntity entity = abTestMapper.selectById(id);
    if (entity == null) {
      OperationResult result = new OperationResult();
      result.setMessage("A/B测试不存在");
      return result;
    }

    // 仅更新非空字段
    if (request.getName() != null) entity.setName(request.getName());
    if (request.getStatus() != null) entity.setStatus(request.getStatus());
    if (request.getDescription() != null) entity.setDescription(request.getDescription());
    if (request.getGroupAVisitors() != null) entity.setGroupAVisitors(request.getGroupAVisitors());
    if (request.getGroupBVisitors() != null) entity.setGroupBVisitors(request.getGroupBVisitors());
    if (request.getGroupAConvRate() != null) entity.setGroupAConvRate(request.getGroupAConvRate());
    if (request.getGroupBConvRate() != null) entity.setGroupBConvRate(request.getGroupBConvRate());
    abTestMapper.updateById(entity);

    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("A/B测试更新成功");
    return result;
  }

  @Override
  public EffectResponse getMarketingEffects(int days) {
    // 查询全部已完成订单
    LambdaQueryWrapper<OrderEntity> totalWrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getStatus, COMPLETED.name());
    List<OrderEntity> allOrders = orderMapper.selectList(totalWrapper);
    BigDecimal totalGmv = allOrders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    int totalOrders = allOrders.size();

    // 查询最近 days 天的已完成订单
    LocalDateTime since = LocalDateTime.now().minusDays(days);
    LambdaQueryWrapper<OrderEntity> recentWrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getStatus, COMPLETED.name())
        .ge(OrderEntity::getCreateTime, since);
    List<OrderEntity> recentOrders = orderMapper.selectList(recentWrapper);
    BigDecimal recentGmv = recentOrders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    int recentOrderCount = recentOrders.size();

    EffectResponse resp = new EffectResponse();
    resp.setTotalGmv(totalGmv);
    resp.setCampaignGmv(recentGmv);
    resp.setCampaignRatio(totalGmv.compareTo(BigDecimal.ZERO) > 0
        ? recentGmv.multiply(BigDecimal.valueOf(100)).divide(totalGmv, 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO);
    resp.setTotalOrders(totalOrders);
    resp.setCampaignOrders(recentOrderCount);
    resp.setAvgDiscount(0);

    // 按天聚合趋势
    List<EffectTrendItem> trend = new ArrayList<>();
    for (int i = days; i >= 0; i--) {
      LocalDate day = LocalDate.now().minusDays(i);
      LocalDateTime dayStart = day.atStartOfDay();
      LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

      BigDecimal dayGmv = allOrders.stream()
          .filter(o -> o.getCreateTime() != null
              && !o.getCreateTime().isBefore(dayStart)
              && o.getCreateTime().isBefore(dayEnd))
          .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      long dayOrders = allOrders.stream()
          .filter(o -> o.getCreateTime() != null
              && !o.getCreateTime().isBefore(dayStart)
              && o.getCreateTime().isBefore(dayEnd))
          .count();

      EffectTrendItem item = new EffectTrendItem();
      item.setDate(day.format(DateTimeFormatter.ofPattern("MM-dd")));
      item.setGmv(dayGmv);
      item.setOrders((int) dayOrders);
      trend.add(item);
    }
    resp.setTrend(trend);
    return resp;
  }

  // ============ 维度：优惠券 ============

  /**
   * 优惠券维度效果：基于 mo_coupon + mo_user_coupon + mo_order(couponDiscount)。
   * <p>
   * 单券 ROI = 带动 GMV / 优惠总额；优惠总额= amount × used(满减) 或 payAmount × used × discountValue(百分比)。
   * 这里简化为 amount × used：避免百分比券的复杂反推，对满减券准确，对百分比券略偏保守。
   */
  @Override
  public CouponEffectResponse getCouponEffects(int days) {
    List<CouponEntity> coupons = couponMapper.selectList(
        new LambdaQueryWrapper<CouponEntity>().orderByDesc(CouponEntity::getTotalCount));

    int totalIssued = 0;
    int totalUsed = 0;
    BigDecimal totalGmv = BigDecimal.ZERO;
    List<CouponEffectItem> items = new ArrayList<>();

    for (CouponEntity c : coupons) {
      int issued = c.getClaimedCount() != null ? c.getClaimedCount() : 0;
      int used = c.getUsedCount() != null ? c.getUsedCount() : 0;
      // 带动 GMV：取最近 days 天使用了该 coupon_id 的已完成订单 payAmount 之和
      BigDecimal couponGmv = BigDecimal.ZERO;
      if (used > 0) {
        List<OrderEntity> orders = orderMapper.selectList(
            new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, COMPLETED.name())
                .like(OrderEntity::getCouponId, c.getId().toString())
                .ge(OrderEntity::getCreateTime, LocalDateTime.now().minusDays(days)));
        couponGmv = orders.stream()
            .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
      }
      totalIssued += issued;
      totalUsed += used;
      totalGmv = totalGmv.add(couponGmv);

      CouponEffectItem item = new CouponEffectItem();
      item.setId(c.getId());
      item.setName(c.getName());
      item.setAmount(c.getDiscountValue() != null ? c.getDiscountValue() : BigDecimal.ZERO);
      item.setIssued(issued);
      item.setUsed(used);
      item.setUsageRate(issued > 0
          ? BigDecimal.valueOf(used * 100L).divide(BigDecimal.valueOf(issued), 1, RoundingMode.HALF_UP)
          : BigDecimal.ZERO);
      BigDecimal discountTotal = item.getAmount().multiply(BigDecimal.valueOf(used));
      item.setRoi(discountTotal.compareTo(BigDecimal.ZERO) > 0
          ? couponGmv.divide(discountTotal, 1, RoundingMode.HALF_UP)
          : BigDecimal.ZERO);
      items.add(item);
    }

    // 按发放量倒序，最多 10 条，避免明细面板过长
    items = items.stream()
        .sorted((a, b) -> Integer.compare(b.getIssued(), a.getIssued()))
        .limit(10)
        .collect(Collectors.toList());

    CouponEffectResponse resp = new CouponEffectResponse();
    resp.setTotalIssued(totalIssued);
    resp.setTotalUsed(totalUsed);
    resp.setUsageRate(totalIssued > 0
        ? BigDecimal.valueOf(totalUsed * 100L).divide(BigDecimal.valueOf(totalIssued), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO);
    resp.setGmv(totalGmv);
    resp.setItems(items);
    return resp;
  }

  // ============ 维度：秒杀 ============

  /**
   * 秒杀维度效果：基于 mo_flash_sale + mo_flash_sale_order。
   * <p>
   * 售罄率 = soldStock / totalStock × 100；售罄时长按 soldStock==totalStock 的最早订单时间估算。
   */
  @Override
  public FlashEffectResponse getFlashEffects(int days) {
    LocalDateTime since = LocalDateTime.now().minusDays(days);
    List<FlashSaleEntity> flashSales = flashSaleMapper.selectList(
        new LambdaQueryWrapper<FlashSaleEntity>().orderByDesc(FlashSaleEntity::getStartTime));

    BigDecimal totalGmv = BigDecimal.ZERO;
    int totalSessions = flashSales.size();
    int soldOutCount = 0;
    long totalSelloutSeconds = 0L;
    int totalOrders = 0;
    int paidOrders = 0;

    List<FlashEffectItem> items = new ArrayList<>();
    for (FlashSaleEntity f : flashSales) {
      int totalStock = f.getTotalStock() != null ? f.getTotalStock() : 0;
      int soldStock = f.getSoldStock() != null ? f.getSoldStock() : 0;
      int rate = totalStock > 0 ? (int) Math.min(100L, soldStock * 100L / totalStock) : 0;
      if (soldStock >= totalStock && totalStock > 0) {
        soldOutCount++;
      }

      // 关联订单：查 flash_sale_order 中属于本次秒杀的订单
      List<FlashSaleOrderEntity> fsOrders = flashSaleOrderMapper.selectList(
          new LambdaQueryWrapper<FlashSaleOrderEntity>()
              .eq(FlashSaleOrderEntity::getFlashSaleId, f.getId())
              .ge(FlashSaleOrderEntity::getCreateTime, since));
      totalOrders += fsOrders.size();

      // 售罄时长：若已售罄且有订单，取最早订单时间与 startTime 的差值
      String detail;
      if (soldStock >= totalStock && totalStock > 0 && !fsOrders.isEmpty()
          && f.getStartTime() != null) {
        FlashSaleOrderEntity earliest = fsOrders.stream()
            .min((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()))
            .orElse(null);
        if (earliest != null) {
          long seconds = java.time.Duration.between(f.getStartTime(), earliest.getCreateTime()).getSeconds();
          totalSelloutSeconds += Math.max(0, seconds);
          long mm = seconds / 60;
          long ss = seconds % 60;
          detail = String.format("售罄 %dm%02ds / %d件", mm, ss, totalStock);
        } else {
          detail = String.format("已售罄 / %d件", totalStock);
        }
      } else if (f.getEndTime() != null && LocalDateTime.now().isAfter(f.getEndTime())) {
        detail = String.format("已结束 售出 %d / %d件", soldStock, totalStock);
      } else if (totalStock > soldStock) {
        detail = String.format("剩余 %d件 / %d件", totalStock - soldStock, totalStock);
      } else {
        detail = String.format("%d / %d件", soldStock, totalStock);
      }

      // 成交率：已支付秒杀订单 / 秒杀下单总数；通过 OrderEntity 表 + orderId 关联判定
      if (!fsOrders.isEmpty()) {
        List<Long> orderIds = fsOrders.stream()
            .map(FlashSaleOrderEntity::getOrderId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
        if (!orderIds.isEmpty()) {
          long paid = orderMapper.selectCount(
              new LambdaQueryWrapper<OrderEntity>()
                  .in(OrderEntity::getId, orderIds)
                  .eq(OrderEntity::getStatus, COMPLETED.name()));
          paidOrders += (int) paid;
        }
      }

      // 单场 GMV：来自该秒杀关联订单的 payAmount 之和（已支付）
      BigDecimal sessionGmv = BigDecimal.ZERO;
      if (!fsOrders.isEmpty()) {
        List<Long> orderIds = fsOrders.stream()
            .map(FlashSaleOrderEntity::getOrderId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
        if (!orderIds.isEmpty()) {
          sessionGmv = orderMapper.selectList(
              new LambdaQueryWrapper<OrderEntity>()
                  .in(OrderEntity::getId, orderIds)
                  .eq(OrderEntity::getStatus, COMPLETED.name()))
              .stream()
              .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
      }
      totalGmv = totalGmv.add(sessionGmv);

      FlashEffectItem item = new FlashEffectItem();
      item.setId(f.getId());
      item.setName(f.getName());
      item.setSelloutRate(rate);
      item.setStatus(rate >= 100 ? "已售罄"
          : (f.getEndTime() != null && LocalDateTime.now().isAfter(f.getEndTime()) ? "已结束" : "进行中"));
      item.setDetail(detail);
      item.setGmv(sessionGmv);
      items.add(item);
    }

    FlashEffectResponse resp = new FlashEffectResponse();
    resp.setGmv(totalGmv);
    resp.setConversionRate(totalOrders > 0
        ? BigDecimal.valueOf(paidOrders * 100L).divide(BigDecimal.valueOf(totalOrders), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO);
    resp.setParticipationRate(totalSessions > 0
        ? BigDecimal.valueOf(soldOutCount * 100L).divide(BigDecimal.valueOf(totalSessions), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO);
    // 平均售罄时长：仅在有售罄场次时分摊，避免除零
    resp.setAvgSelloutMinutes(soldOutCount > 0
        ? BigDecimal.valueOf(totalSelloutSeconds).divide(BigDecimal.valueOf(soldOutCount * 60L), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO);
    resp.setItems(items);
    return resp;
  }

  // ============ 维度：分销佣金 ============

  /**
   * 分销维度效果：基于 mo_invite 推广关系 + mo_order 订单金额 + 固定佣金率 10%。
   * <p>
   * 分销员 = 有 inviteCode 且推广过订单的 userId；GMV/佣金按 invitedUserId 的订单金额聚合。
   * 渠道占比按 payChannel 维度聚合（自然流量/付费推广/分销渠道三段）。
   */
  @Override
  public DistributionEffectResponse getDistributionEffects(int days) {
    LocalDateTime since = LocalDateTime.now().minusDays(days);

    // 1. 全部邀请关系：分销员 = userId（推广人）
    List<InviteEntity> invites = inviteMapper.selectList(
        new LambdaQueryWrapper<InviteEntity>().ge(InviteEntity::getCreateTime, since));

    // userId(分销员) -> invitedUserId 列表
    Map<Long, List<Long>> distributorMap = new HashMap<>();
    for (InviteEntity inv : invites) {
      if (inv.getUserId() == null || inv.getInvitedUserId() == null) continue;
      distributorMap.computeIfAbsent(inv.getUserId(), k -> new ArrayList<>()).add(inv.getInvitedUserId());
    }

    // 2. 推广订单 = 受邀用户最近 days 天已完成订单
    BigDecimal commissionRate = new BigDecimal("0.10");
    Map<Long, BigDecimal> distributorGmv = new HashMap<>();
    Map<Long, Integer> distributorOrders = new HashMap<>();
    BigDecimal distributionGmv = BigDecimal.ZERO;
    int distributorWithOrderCount = 0;

    for (Map.Entry<Long, List<Long>> entry : distributorMap.entrySet()) {
      Long distributorId = entry.getKey();
      List<Long> invitedIds = entry.getValue();
      List<OrderEntity> orders = orderMapper.selectList(
          new LambdaQueryWrapper<OrderEntity>()
              .in(OrderEntity::getUserId, invitedIds)
              .eq(OrderEntity::getStatus, COMPLETED.name())
              .ge(OrderEntity::getCreateTime, since));
      if (orders.isEmpty()) continue;
      BigDecimal sum = orders.stream()
          .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      distributorGmv.put(distributorId, sum);
      distributorOrders.put(distributorId, orders.size());
      distributionGmv = distributionGmv.add(sum);
      distributorWithOrderCount++;
    }

    // 3. 总 GMV（分母，用于占比计算）
    List<OrderEntity> allRecentOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .eq(OrderEntity::getStatus, COMPLETED.name())
            .ge(OrderEntity::getCreateTime, since));
    BigDecimal totalGmv = allRecentOrders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal naturalGmv = totalGmv.subtract(distributionGmv);
    if (naturalGmv.compareTo(BigDecimal.ZERO) < 0) naturalGmv = BigDecimal.ZERO;

    // 4. 渠道占比（自然流量 / 分销渠道；付费推广暂以分销 GMV * 1.0 估算并附注释）
    List<DistributionChannelShare> channels = new ArrayList<>();
    if (totalGmv.compareTo(BigDecimal.ZERO) > 0) {
      int naturalPct = naturalGmv.multiply(BigDecimal.valueOf(100))
          .divide(totalGmv, 0, RoundingMode.HALF_UP).intValue();
      int distPct = distributionGmv.multiply(BigDecimal.valueOf(100))
          .divide(totalGmv, 0, RoundingMode.HALF_UP).intValue();
      int paidPct = 100 - naturalPct - distPct;
      if (paidPct < 0) paidPct = 0;
      channels.add(newChannel("自然流量", naturalPct));
      channels.add(newChannel("分销渠道", distPct));
      channels.add(newChannel("付费推广", paidPct));
    } else {
      channels.add(newChannel("自然流量", 100));
      channels.add(newChannel("分销渠道", 0));
      channels.add(newChannel("付费推广", 0));
    }

    // 5. Top 分销员排行（按 GMV 倒序，取前 7）
    List<DistributionTopItem> topList = distributorGmv.entrySet().stream()
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .limit(7)
        .map(e -> {
          DistributionTopItem t = new DistributionTopItem();
          t.setUserId(e.getKey());
          t.setName("分销员" + e.getKey());
          t.setOrders(distributorOrders.getOrDefault(e.getKey(), 0));
          t.setGmv(e.getValue());
          t.setCommission(e.getValue().multiply(commissionRate).setScale(2, RoundingMode.HALF_UP));
          return t;
        })
        .collect(Collectors.toList());

    DistributionEffectResponse resp = new DistributionEffectResponse();
    resp.setDistributorCount(distributorMap.size());
    resp.setActiveRate(distributorMap.size() > 0
        ? BigDecimal.valueOf(distributorWithOrderCount * 100L)
            .divide(BigDecimal.valueOf(distributorMap.size()), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO);
    resp.setGmv(distributionGmv);
    resp.setCommission(distributionGmv.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP));
    resp.setChannels(channels);
    resp.setTopList(topList);
    return resp;
  }

  private static DistributionChannelShare newChannel(String name, int ratio) {
    DistributionChannelShare s = new DistributionChannelShare();
    s.setName(name);
    s.setRatio(ratio);
    return s;
  }

  /**
   * 根据时间计算活动状态
   */
  private String calculateStatus(LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null) return "UPCOMING";
    LocalDateTime now = LocalDateTime.now();
    if (now.isBefore(start)) return "UPCOMING";
    if (now.isAfter(end)) return "ENDED";
    return "ACTIVE";
  }

  /**
   * 解析日期字符串,兼容 yyyy-MM-dd、ISO_LOCAL_DATE_TIME 与带时区的 ISO-8601（如 2026-08-17T16:00:00.000Z）。
   * <p>
   * 历史 Bug：直接使用 ISO_LOCAL_DATE_TIME 解析时，前端返回的 UTC 时间字符串（带 Z 后缀）会在 index 23 处
   * 抛 DateTimeParseException，导致活动创建接口 500。这里先尝试按带时区格式解析，统一转换为本地时区。
   */
  private LocalDateTime parseDateTime(String dateStr) {
    if (dateStr == null) return null;
    String s = dateStr.trim();
    if (s.isEmpty()) return null;
    if (s.length() <= 10) {
      return LocalDateTime.parse(s + " 00:00:00",
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    try {
      // 优先按带时区的 ISO-8601 解析（前端 el-date-picker 默认输出格式可能为 UTC 字符串）
      return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
          .atZoneSameInstant(ZoneId.systemDefault())
          .toLocalDateTime();
    } catch (DateTimeParseException ignore) {
      // 回退：按无时区 ISO_LOCAL_DATE_TIME 解析（如 "2026-08-17T16:00:00"）
      return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
  }
}

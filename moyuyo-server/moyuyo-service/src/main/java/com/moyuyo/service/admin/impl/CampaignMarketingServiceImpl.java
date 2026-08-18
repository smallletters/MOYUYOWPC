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
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.OrderMapper;
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
import java.util.List;
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

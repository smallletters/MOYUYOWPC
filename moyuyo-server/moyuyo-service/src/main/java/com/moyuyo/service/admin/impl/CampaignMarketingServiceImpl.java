package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 营销活动服务实现
 */
@Service
@RequiredArgsConstructor
public class CampaignMarketingServiceImpl implements CampaignMarketingService {

  private final MarketingCampaignMapper marketingCampaignMapper;
  private final AbTestMapper abTestMapper;
  private final OrderMapper orderMapper;

  @Override
  public Map<String, Object> listCampaigns(int page, int size) {
    IPage<MarketingCampaignEntity> pageResult = marketingCampaignMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<MarketingCampaignEntity>()
            .orderByDesc(MarketingCampaignEntity::getCreateTime));

    List<Map<String, Object>> list = pageResult.getRecords().stream().map(c -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", c.getId());
      item.put("name", c.getName());
      item.put("status", c.getStatus());
      item.put("description", c.getDescription());
      item.put("startDate", c.getStartDate() != null
          ? c.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null);
      item.put("endDate", c.getEndDate() != null
          ? c.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null);
      item.put("participants", c.getParticipants());
      item.put("gmv", c.getGmv());
      item.put("budget", c.getBudget());
      return item;
    }).collect(Collectors.toList());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return result;
  }

  @Override
  public Map<String, Object> createCampaign(Map<String, Object> data) {
    MarketingCampaignEntity entity = new MarketingCampaignEntity();
    entity.setName((String) data.get("name"));
    entity.setStatus((String) data.get("status"));
    entity.setDescription((String) data.get("description"));
    if (data.get("startDate") != null) {
      entity.setStartDate(LocalDateTime.parse(data.get("startDate") + " 00:00:00",
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    if (data.get("endDate") != null) {
      entity.setEndDate(LocalDateTime.parse(data.get("endDate") + " 00:00:00",
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    if (data.get("budget") != null) {
      entity.setBudget(new BigDecimal(data.get("budget").toString()));
    }
    marketingCampaignMapper.insert(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("name", entity.getName());
    result.put("message", "活动创建成功");
    return result;
  }

  @Override
  public Map<String, Object> getCampaignDetail(Long id) {
    MarketingCampaignEntity entity = marketingCampaignMapper.selectById(id);
    if (entity == null) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("message", "活动不存在");
      return error;
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", entity.getId());
    data.put("name", entity.getName());
    data.put("status", entity.getStatus());
    data.put("description", entity.getDescription());
    data.put("startDate", entity.getStartDate() != null
        ? entity.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null);
    data.put("endDate", entity.getEndDate() != null
        ? entity.getEndDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null);
    data.put("participants", entity.getParticipants());
    data.put("gmv", entity.getGmv());
    data.put("budget", entity.getBudget());
    data.put("cost", entity.getCost());

    // 查询已完成订单统计
    LambdaQueryWrapper<OrderEntity> orderWrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getStatus, "COMPLETED");
    Long totalOrders = orderMapper.selectCount(orderWrapper);
    BigDecimal totalPayAmount = orderMapper.selectList(orderWrapper).stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal avgOrderValue = totalOrders > 0
        ? totalPayAmount.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    Map<String, Object> effects = new LinkedHashMap<>();
    effects.put("orderIncrease", 0);
    effects.put("conversionIncrease", 0);
    effects.put("avgOrderValue", avgOrderValue);
    effects.put("roi", entity.getGmv() != null && entity.getBudget() != null
        && entity.getBudget().compareTo(BigDecimal.ZERO) > 0
        ? entity.getGmv().divide(entity.getBudget(), 2, RoundingMode.HALF_UP)
        : 0);
    data.put("effects", effects);
    return data;
  }

  @Override
  public Map<String, Object> updateCampaign(Long id, Map<String, Object> data) {
    MarketingCampaignEntity entity = marketingCampaignMapper.selectById(id);
    if (entity == null) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("message", "活动不存在");
      return error;
    }
    if (data.get("name") != null) entity.setName((String) data.get("name"));
    if (data.get("status") != null) entity.setStatus((String) data.get("status"));
    if (data.get("description") != null) entity.setDescription((String) data.get("description"));
    if (data.get("startDate") != null) {
      entity.setStartDate(LocalDateTime.parse(data.get("startDate") + " 00:00:00",
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    if (data.get("endDate") != null) {
      entity.setEndDate(LocalDateTime.parse(data.get("endDate") + " 00:00:00",
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
    if (data.get("budget") != null) {
      entity.setBudget(new BigDecimal(data.get("budget").toString()));
    }
    marketingCampaignMapper.updateById(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("name", entity.getName());
    result.put("message", "活动更新成功");
    return result;
  }

  @Override
  public Map<String, Object> deleteCampaign(Long id) {
    MarketingCampaignEntity entity = marketingCampaignMapper.selectById(id);
    Map<String, Object> result = new LinkedHashMap<>();
    if (entity == null) {
      result.put("message", "活动不存在");
      return result;
    }
    marketingCampaignMapper.deleteById(id);
    result.put("id", id);
    result.put("message", "活动删除成功");
    return result;
  }

  @Override
  public List<Map<String, Object>> listAbTests() {
    List<AbTestEntity> records = abTestMapper.selectList(
        new LambdaQueryWrapper<AbTestEntity>().orderByDesc(AbTestEntity::getCreateTime));
    return records.stream().map(t -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", t.getId());
      item.put("name", t.getName());
      item.put("status", t.getStatus());
      item.put("description", t.getDescription());
      item.put("groupAVisitors", t.getGroupAVisitors());
      item.put("groupBVisitors", t.getGroupBVisitors());
      item.put("groupAConvRate", t.getGroupAConvRate());
      item.put("groupBConvRate", t.getGroupBConvRate());
      item.put("startTime", t.getStartTime());
      return item;
    }).collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> createAbTest(Map<String, Object> data) {
    AbTestEntity entity = new AbTestEntity();
    entity.setName((String) data.get("name"));
    entity.setStatus((String) data.get("status"));
    entity.setDescription((String) data.get("description"));
    if (data.get("groupAVisitors") != null) {
      entity.setGroupAVisitors(Integer.valueOf(data.get("groupAVisitors").toString()));
    }
    if (data.get("groupBVisitors") != null) {
      entity.setGroupBVisitors(Integer.valueOf(data.get("groupBVisitors").toString()));
    }
    if (data.get("groupAConvRate") != null) {
      entity.setGroupAConvRate(new BigDecimal(data.get("groupAConvRate").toString()));
    }
    if (data.get("groupBConvRate") != null) {
      entity.setGroupBConvRate(new BigDecimal(data.get("groupBConvRate").toString()));
    }
    entity.setStartTime(LocalDateTime.now());
    abTestMapper.insert(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", entity.getId());
    result.put("name", entity.getName());
    result.put("message", "A/B测试创建成功");
    return result;
  }

  @Override
  public Map<String, Object> getMarketingEffects(int days) {
    LambdaQueryWrapper<OrderEntity> totalWrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getStatus, "COMPLETED");
    List<OrderEntity> allOrders = orderMapper.selectList(totalWrapper);
    BigDecimal totalGmv = allOrders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    int totalOrders = allOrders.size();

    LocalDateTime since = LocalDateTime.now().minusDays(days);
    LambdaQueryWrapper<OrderEntity> recentWrapper = new LambdaQueryWrapper<OrderEntity>()
        .eq(OrderEntity::getStatus, "COMPLETED")
        .ge(OrderEntity::getCreateTime, since);
    List<OrderEntity> recentOrders = orderMapper.selectList(recentWrapper);
    BigDecimal recentGmv = recentOrders.stream()
        .map(o -> o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    int recentOrderCount = recentOrders.size();

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("totalGmv", totalGmv);
    data.put("campaignGmv", recentGmv);
    data.put("campaignRatio", totalGmv.compareTo(BigDecimal.ZERO) > 0
        ? recentGmv.multiply(BigDecimal.valueOf(100)).divide(totalGmv, 1, RoundingMode.HALF_UP)
        : 0);
    data.put("totalOrders", totalOrders);
    data.put("campaignOrders", recentOrderCount);
    data.put("avgDiscount", 0);

    // 按天聚合趋势
    List<Map<String, Object>> trend = new ArrayList<>();
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

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("date", day.format(DateTimeFormatter.ofPattern("MM-dd")));
      item.put("gmv", dayGmv);
      item.put("orders", (int) dayOrders);
      trend.add(item);
    }
    data.put("trend", trend);
    return data;
  }
}

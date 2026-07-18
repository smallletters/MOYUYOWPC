package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moyuyo.dao.entity.BrowsingHistoryEntity;
import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.admin.entity.SearchLogEntity;
import com.moyuyo.dao.admin.entity.VisitLogEntity;
import com.moyuyo.dao.admin.mapper.SearchLogMapper;
import com.moyuyo.dao.admin.mapper.VisitLogMapper;
import com.moyuyo.dao.mapper.BrowsingHistoryMapper;
import com.moyuyo.dao.mapper.CartMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.admin.AdminAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理后台数据分析服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminAnalysisServiceImpl implements AdminAnalysisService {

  private final OrderMapper orderMapper;
  private final ProductMapper productMapper;
  private final UserMapper userMapper;
  private final BrowsingHistoryMapper browsingHistoryMapper;
  private final CartMapper cartMapper;
  private final SearchLogMapper searchLogMapper;
  private final VisitLogMapper visitLogMapper;

  @Override
  public List<Map<String, Object>> funnel() {
    // 漏斗各阶段：从浏览到完成的独立用户数
    long browseUsers = countDistinctUsers("浏览商品", null, null);
    long cartUsers = countDistinctCartUsers();
    long orderUsers = countDistinctOrderUsers(null);
    long paidUsers = countDistinctOrderUsers("PAID");
    long completedUsers = countDistinctOrderUsers("COMPLETED");

    long[] stages = {browseUsers, cartUsers, orderUsers, paidUsers, completedUsers};
    String[] stageNames = {"浏览商品", "加入购物车", "提交订单", "支付成功", "完成交易"};

    List<Map<String, Object>> list = new ArrayList<>();
    for (int i = 0; i < stageNames.length; i++) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("stage", stageNames[i]);
      item.put("userCount", stages[i]);
      // 转化率 = 当前阶段 / 第一阶段 * 100
      BigDecimal rate = browseUsers > 0
          ? BigDecimal.valueOf(stages[i])
              .multiply(BigDecimal.valueOf(100))
              .divide(BigDecimal.valueOf(browseUsers), 1, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;
      item.put("conversionRate", rate);
      list.add(item);
    }
    return list;
  }

  @Override
  public List<Map<String, Object>> rfm() {
    // 从数据库查询真实 RFM 聚合数据
    List<Map<String, Object>> rawData = orderMapper.selectRfmData();

    if (rawData.isEmpty()) {
      return Collections.emptyList();
    }

    // 提取 R/F/M 值计算分位数阈值
    List<Long> rValues = rawData.stream()
        .map(m -> ((Number) m.get("rDays")).longValue())
        .sorted().collect(Collectors.toList());
    List<Long> fValues = rawData.stream()
        .map(m -> ((Number) m.get("fCount")).longValue())
        .sorted().collect(Collectors.toList());
    List<BigDecimal> mValues = rawData.stream()
        .map(m -> new BigDecimal(m.get("mAmount").toString()))
        .sorted().collect(Collectors.toList());

    long rLow = percentileLong(rValues, 33);   // R 越小越好，低分段为高价值
    long rHigh = percentileLong(rValues, 66);
    long fLow = percentileLong(fValues, 33);
    long fHigh = percentileLong(fValues, 66);
    BigDecimal mLow = percentileDecimal(mValues, 33);
    BigDecimal mHigh = percentileDecimal(mValues, 66);

    // 计算评分并划分 RFM 等级
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : rawData) {
      Map<String, Object> item = new LinkedHashMap<>();
      long rDays = ((Number) row.get("rDays")).longValue();
      long fCount = ((Number) row.get("fCount")).longValue();
      BigDecimal mAmount = new BigDecimal(row.get("mAmount").toString());

      // R 评分：天数越少得分越高（取反）
      int rScore = rDays <= rLow ? 5 : rDays <= rHigh ? 3 : 1;
      int fScore = fCount >= fHigh ? 5 : fCount >= fLow ? 3 : 1;
      int mScore = mAmount.compareTo(mHigh) >= 0 ? 5
          : mAmount.compareTo(mLow) >= 0 ? 3 : 1;

      item.put("customerName", row.get("customerName"));
      item.put("rDays", rDays);
      item.put("fCount", fCount);
      item.put("mAmount", mAmount);
      item.put("rfmLevel", classifyRfm(rScore, fScore, mScore));
      result.add(item);
    }
    return result;
  }

  @Override
  public Map<String, Object> searchStats() {
    Map<String, Object> result = new LinkedHashMap<>();

    long totalProducts = productMapper.selectCount(null);
    long totalUsers = userMapper.selectCount(null);

    // 总搜索次数
    long totalSearches = searchLogMapper.selectCount(null);

    // 独立搜索用户数
    List<Object> searchUsersResult = searchLogMapper.selectObjs(
        new QueryWrapper<SearchLogEntity>()
            .select("COUNT(DISTINCT user_id)")
    );
    long searchUsers = searchUsersResult.isEmpty() || searchUsersResult.get(0) == null
        ? 0 : ((Number) searchUsersResult.get(0)).longValue();

    // 无结果搜索数（result_count = 0）
    List<Object> noResultResult = searchLogMapper.selectObjs(
        new QueryWrapper<SearchLogEntity>()
            .select("COUNT(*)")
            .eq("result_count", 0)
    );
    long noResultCount = noResultResult.isEmpty() || noResultResult.get(0) == null
        ? 0 : ((Number) noResultResult.get(0)).longValue();

    // 无结果率
    BigDecimal noResultRate = totalSearches > 0
        ? BigDecimal.valueOf(noResultCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalSearches), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    // 人均搜索次数
    BigDecimal avgSearchesPerUser = searchUsers > 0
        ? BigDecimal.valueOf(totalSearches)
            .divide(BigDecimal.valueOf(searchUsers), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    // 热门搜索关键词 TOP10
    List<Map<String, Object>> hotKeywords = searchLogMapper.selectMaps(
        new QueryWrapper<SearchLogEntity>()
            .select("keyword, COUNT(*) as count")
            .groupBy("keyword")
            .orderByDesc("count")
            .last("LIMIT 10")
    );

    result.put("totalSearches", totalSearches);
    result.put("searchUsers", searchUsers);
    result.put("noResultRate", noResultRate);
    result.put("avgSearchesPerUser", avgSearchesPerUser);
    result.put("totalProducts", totalProducts);
    result.put("totalUsers", totalUsers);
    result.put("hotKeywords", hotKeywords);
    return result;
  }

  @Override
  public Map<String, Object> trafficStats() {
    Map<String, Object> result = new LinkedHashMap<>();

    // 今日订单数
    long todayOrders = orderMapper.selectCount(
        new LambdaQueryWrapper<OrderEntity>()
            .apply("DATE(create_time) = CURDATE()"));

    // 今日独立访客（按 session_id 去重）
    List<Object> visitorsResult = visitLogMapper.selectObjs(
        new QueryWrapper<VisitLogEntity>()
            .select("COUNT(DISTINCT session_id)")
            .apply("DATE(create_time) = CURDATE()")
    );
    long todayVisitors = visitorsResult.isEmpty() || visitorsResult.get(0) == null
        ? 0 : ((Number) visitorsResult.get(0)).longValue();

    // 今日页面浏览量（PV）
    long todayPageViews = visitLogMapper.selectCount(
        new QueryWrapper<VisitLogEntity>()
            .apply("DATE(create_time) = CURDATE()")
    );

    // 跳出率 = 单页会话数 / 总会话数
    List<Object> bouncedResult = visitLogMapper.selectObjs(
        new QueryWrapper<VisitLogEntity>()
            .select("COUNT(*)")
            .inSql("session_id",
                "SELECT session_id FROM mo_visit_log WHERE DATE(create_time) = CURDATE() GROUP BY session_id HAVING COUNT(*) = 1")
            .apply("DATE(create_time) = CURDATE()")
    );
    long bouncedSessions = bouncedResult.isEmpty() || bouncedResult.get(0) == null
        ? 0 : ((Number) bouncedResult.get(0)).longValue();

    BigDecimal bounceRate = todayVisitors > 0
        ? BigDecimal.valueOf(bouncedSessions)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(todayVisitors), 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    // 平均停留时长
    List<Object> avgStayResult = visitLogMapper.selectObjs(
        new QueryWrapper<VisitLogEntity>()
            .select("COALESCE(AVG(stay_duration), 0)")
            .apply("DATE(create_time) = CURDATE()")
    );
    Object avgStayObj = avgStayResult.isEmpty() ? null : avgStayResult.get(0);
    String avgStayDuration = avgStayObj != null
        ? Math.round(((Number) avgStayObj).doubleValue()) + "秒"
        : "暂无数据";

    // 按渠道分组统计流量分布
    List<Map<String, Object>> channels = visitLogMapper.selectMaps(
        new QueryWrapper<VisitLogEntity>()
            .select("COALESCE(channel, '直接访问') as channel, COUNT(DISTINCT session_id) as visits, COUNT(*) as pv")
            .groupBy("channel")
            .orderByDesc("visits")
    );

    result.put("todayVisitors", todayVisitors);
    result.put("todayPageViews", todayPageViews);
    result.put("todayOrders", todayOrders);
    result.put("bounceRate", bounceRate);
    result.put("avgStayDuration", avgStayDuration);
    result.put("channels", channels);
    return result;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 统计浏览商品的独立用户数
   */
  private long countDistinctUsers(String stage, Object param1, Object param2) {
    QueryWrapper<BrowsingHistoryEntity> wrapper = new QueryWrapper<>();
    wrapper.select("COUNT(DISTINCT user_id)");
    List<Object> result = browsingHistoryMapper.selectObjs(wrapper);
    return result.isEmpty() || result.get(0) == null ? 0 : ((Number) result.get(0)).longValue();
  }

  /**
   * 统计加入购物车的独立用户数
   */
  private long countDistinctCartUsers() {
    QueryWrapper<CartEntity> wrapper = new QueryWrapper<>();
    wrapper.select("COUNT(DISTINCT user_id)");
    List<Object> result = cartMapper.selectObjs(wrapper);
    return result.isEmpty() || result.get(0) == null ? 0 : ((Number) result.get(0)).longValue();
  }

  /**
   * 统计下单/支付/完成的独立用户数
   */
  private long countDistinctOrderUsers(String stage) {
    QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();
    wrapper.select("COUNT(DISTINCT user_id)");
    if ("PAID".equals(stage)) {
      wrapper.isNotNull("paid_at");
    } else if ("COMPLETED".equals(stage)) {
      wrapper.isNotNull("received_time");
    }
    List<Object> result = orderMapper.selectObjs(wrapper);
    return result.isEmpty() || result.get(0) == null ? 0 : ((Number) result.get(0)).longValue();
  }

  /**
   * 计算 List<Long> 的百分位值
   */
  private long percentileLong(List<Long> sorted, int pct) {
    if (sorted.isEmpty()) return 0;
    int idx = Math.min(sorted.size() * pct / 100, sorted.size() - 1);
    return sorted.get(idx);
  }

  /**
   * 计算 List<BigDecimal> 的百分位值
   */
  private BigDecimal percentileDecimal(List<BigDecimal> sorted, int pct) {
    if (sorted.isEmpty()) return BigDecimal.ZERO;
    int idx = Math.min(sorted.size() * pct / 100, sorted.size() - 1);
    return sorted.get(idx);
  }

  /**
   * 根据 R/F/M 评分划分客户等级
   */
  private String classifyRfm(int rScore, int fScore, int mScore) {
    int total = rScore + fScore + mScore;
    if (total >= 13) return "高价值";
    if (total >= 9) return "一般价值";
    if (total >= 6) return "一般发展";
    if (rScore <= 2) return "流失";
    return "一般挽留";
  }
}

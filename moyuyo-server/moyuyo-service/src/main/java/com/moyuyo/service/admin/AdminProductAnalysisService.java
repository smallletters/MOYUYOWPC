package com.moyuyo.service.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 商品分析服务接口
 */
public interface AdminProductAnalysisService {

  /**
   * 商品分析概览（总商品数、在售数、总浏览量、总销量、总收藏数）
   */
  Map<String, Object> overview();

  /**
   * 销量排行
   */
  List<Map<String, Object>> topSales(int limit);

  /**
   * 商品报表（从数据库查询商品和订单数据，支持日期范围筛选）
   *
   * @param startDate 开始日期（可选）
   * @param endDate   结束日期（可选）
   */
  List<Map<String, Object>> report(LocalDate startDate, LocalDate endDate);

  // ==================== 商品分析各模块（真实数据） ====================

  /**
   * 新品表现追踪：近 N 天上架商品的销量、转化率、上架时间列表
   *
   * @param days 窗口天数（7/14/30）
   * @return { metrics: [{label,value,change,trend}], list: [{name,launchAt,sales,conversion}] }
   */
  Map<String, Object> newProductTracking(int days);

  /**
   * 滞销商品预警：上架 ≥30 天且近 30 天销量 ≤ 阈值的商品
   *
   * @return [{name, stock, sales30, days, level}]
   */
  List<Map<String, Object>> slowMovingProducts();

  /**
   * 流转率概览：平均流转率/加购率/收藏率 + 按分类的浏览/成交
   *
   * @return { metrics:[{label,value}], categories:[{name,views,deals,rate,level}] }
   */
  Map<String, Object> turnoverOverview();

  /**
   * 热门搜索词 Top 10（来自 mo_search_log，按关键词分组计数）
   *
   * @return [{keyword,count,cartRate,percent}]
   */
  List<Map<String, Object>> hotSearchKeywords();

  /**
   * 评价分析概览：好评/中评/差评 + 评分分布
   *
   * @return { positive, neutral, negative, total, distribution:[{stars,percent,count}] }
   */
  Map<String, Object> reviewAnalysis();

  /**
   * 高频评价关键词：从最近 N 条已通过评价的 content 字段中按 2-6 字片段频次聚合
   *
   * @return [{keyword,count,level}]
   */
  List<Map<String, Object>> reviewKeywords();

  /**
   * 库存健康度：总库存、健康占比、按状态（滞销/正常/紧缺）的商品数和占比
   *
   * @return { totalStock, healthRate, items:[{label,count,percent,level}] }
   */
  Map<String, Object> inventoryHealth();

  /**
   * 库存周转天数排行：周转天数 = 库存 / max(近30天日均销量, 1)
   *
   * @return [{name, stock, days, level}]
   */
  List<Map<String, Object>> inventoryTurnoverRanking();
}

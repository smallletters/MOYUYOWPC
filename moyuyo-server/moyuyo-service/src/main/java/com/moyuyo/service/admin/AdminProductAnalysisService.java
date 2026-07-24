package com.moyuyo.service.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 商品分析服务接口
 */
public interface AdminProductAnalysisService {

  /**
   * 商品分析概览（总商品数、总销量、总收藏等）
   */
  Map<String, Object> overview();

  /**
   * 销量排行
   */
  List<Map<String, Object>> topSales(int limit);

  /**
   * 类目分布
   */
  List<Map<String, Object>> categoryDistribution();

  /**
   * 价格区间分布
   */
  List<Map<String, Object>> priceDistribution();

  /**
   * 商品报表（从数据库查询商品和订单数据，支持日期范围筛选）
   * @param startDate 开始日期（可选）
   * @param endDate 结束日期（可选）
   */
  List<Map<String, Object>> report(LocalDate startDate, LocalDate endDate);
}

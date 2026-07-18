package com.moyuyo.service.admin;

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
   * 商品报表（从数据库查询商品和订单数据，替代模拟数据）
   */
  List<Map<String, Object>> report();
}

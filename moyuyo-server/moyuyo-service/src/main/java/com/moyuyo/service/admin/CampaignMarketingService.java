package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台营销活动服务
 */
public interface CampaignMarketingService {

  /**
   * 营销活动列表
   */
  Map<String, Object> listCampaigns(int page, int size);

  /**
   * 创建营销活动
   */
  Map<String, Object> createCampaign(Map<String, Object> data);

  /**
   * 活动详情
   */
  Map<String, Object> getCampaignDetail(Long id);

  /**
   * 更新活动
   */
  Map<String, Object> updateCampaign(Long id, Map<String, Object> data);

  /**
   * 删除活动
   */
  Map<String, Object> deleteCampaign(Long id);

  /**
   * A/B测试列表
   */
  List<Map<String, Object>> listAbTests();

  /**
   * 创建A/B测试
   */
  Map<String, Object> createAbTest(Map<String, Object> data);

  /**
   * 营销效果统计
   */
  Map<String, Object> getMarketingEffects(int days);
}

package com.moyuyo.service.admin;

import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.campaign.*;

import java.util.List;

/**
 * 管理后台营销活动服务
 */
public interface CampaignMarketingService {

  /**
   * 营销活动列表(分页)
   */
  PageResponse<CampaignResponse> listCampaigns(int page, int size);

  /**
   * 创建营销活动
   *
   * @return 包含 id 和 message 的操作结果
   */
  OperationResult createCampaign(CampaignRequest request);

  /**
   * 活动详情(含效果统计)
   *
   * @return 详情响应,活动不存在时返回 null
   */
  CampaignDetailResponse getCampaignDetail(Long id);

  /**
   * 更新活动
   *
   * @return 操作结果,活动不存在时 message 为 "活动不存在"
   */
  OperationResult updateCampaign(Long id, CampaignRequest request);

  /**
   * 删除活动
   *
   * @return 操作结果,活动不存在时 message 为 "活动不存在"
   */
  OperationResult deleteCampaign(Long id);

  /**
   * A/B 测试列表
   */
  List<AbTestResponse> listAbTests();

  /**
   * 创建 A/B 测试
   */
  OperationResult createAbTest(AbTestRequest request);

  /**
   * 更新 A/B 测试
   *
   * @return 操作结果,测试不存在时 message 为 "A/B测试不存在"
   */
  OperationResult updateAbTest(Long id, AbTestRequest request);

  /**
   * 营销效果统计(最近 days 天)
   */
  EffectResponse getMarketingEffects(int days);
}

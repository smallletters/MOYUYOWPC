package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.campaign.*;
import com.moyuyo.service.admin.CampaignMarketingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理后台营销活动 Controller 单元测试
 * 覆盖 9 个端点:campaigns / createCampaign / updateCampaign / campaignDetail
 *              deleteCampaign / abTests / createAbTest / updateAbTest / effects
 */
@ExtendWith(MockitoExtension.class)
class CampaignMarketingControllerTest {

  @Mock
  private CampaignMarketingService campaignMarketingService;

  @InjectMocks
  private CampaignMarketingController controller;

  // ============ campaigns ============

  @Test
  void campaigns_正常调用_委托Service并返回分页结果() {
    // given
    PageResponse<CampaignResponse> pageResp = new PageResponse<>();
    pageResp.setRecords(List.of());
    pageResp.setTotal(0);
    when(campaignMarketingService.listCampaigns(1, 15)).thenReturn(pageResp);

    // when
    Result<PageResponse<CampaignResponse>> result = controller.campaigns(1, 15);

    // then
    verify(campaignMarketingService).listCampaigns(1, 15);
    assertEquals(0, result.getCode());
    assertEquals(0, result.getData().getTotal());
  }

  // ============ createCampaign ============

  @Test
  void createCampaign_有效请求_委托Service并返回操作结果() {
    // given
    CampaignRequest request = new CampaignRequest();
    request.setName("618大促");
    request.setBudget(new BigDecimal("10000"));
    OperationResult op = new OperationResult();
    op.setId(1L);
    op.setMessage("活动创建成功");
    when(campaignMarketingService.createCampaign(any())).thenReturn(op);

    // when
    Result<OperationResult> result = controller.createCampaign(request);

    // then:验证参数被正确传递
    ArgumentCaptor<CampaignRequest> captor = ArgumentCaptor.forClass(CampaignRequest.class);
    verify(campaignMarketingService).createCampaign(captor.capture());
    assertEquals("618大促", captor.getValue().getName());
    assertEquals(new BigDecimal("10000"), captor.getValue().getBudget());

    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("活动创建成功", result.getData().getMessage());
  }

  // ============ updateCampaign ============

  @Test
  void updateCampaign_有效请求_委托Service并返回操作结果() {
    CampaignRequest request = new CampaignRequest();
    request.setName("更新后名称");
    OperationResult op = new OperationResult();
    op.setId(1L);
    op.setMessage("活动更新成功");
    when(campaignMarketingService.updateCampaign(eq(1L), any())).thenReturn(op);

    Result<OperationResult> result = controller.updateCampaign(1L, request);

    verify(campaignMarketingService).updateCampaign(eq(1L), eq(request));
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
  }

  // ============ campaignDetail ============

  @Test
  void campaignDetail_活动存在_返回200和详情() {
    CampaignDetailResponse detail = new CampaignDetailResponse();
    CampaignResponse campaign = new CampaignResponse();
    campaign.setId(1L);
    campaign.setName("618大促");
    detail.setCampaign(campaign);
    when(campaignMarketingService.getCampaignDetail(1L)).thenReturn(detail);

    Result<CampaignDetailResponse> result = controller.campaignDetail(1L);

    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getCampaign().getId());
    assertEquals("618大促", result.getData().getCampaign().getName());
  }

  @Test
  void campaignDetail_活动不存在_返回404() {
    when(campaignMarketingService.getCampaignDetail(1L)).thenReturn(null);

    Result<CampaignDetailResponse> result = controller.campaignDetail(1L);

    assertEquals(404, result.getCode());
    assertEquals("活动不存在", result.getMessage());
    assertNull(result.getData());
  }

  // ============ deleteCampaign ============

  @Test
  void deleteCampaign_活动存在_返回操作结果() {
    OperationResult op = new OperationResult();
    op.setId(1L);
    op.setMessage("活动删除成功");
    when(campaignMarketingService.deleteCampaign(1L)).thenReturn(op);

    Result<OperationResult> result = controller.deleteCampaign(1L);

    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
    assertEquals("活动删除成功", result.getData().getMessage());
  }

  // ============ abTests ============

  @Test
  void abTests_正常调用_返回VO列表() {
    AbTestResponse vo = new AbTestResponse();
    vo.setId(1L);
    vo.setName("AB测试1");
    when(campaignMarketingService.listAbTests()).thenReturn(List.of(vo));

    Result<List<AbTestResponse>> result = controller.abTests();

    assertEquals(0, result.getCode());
    assertEquals(1, result.getData().size());
    assertEquals("AB测试1", result.getData().get(0).getName());
  }

  // ============ createAbTest ============

  @Test
  void createAbTest_有效请求_委托Service并返回操作结果() {
    AbTestRequest request = new AbTestRequest();
    request.setName("新测试");
    request.setGroupAVisitors(50);
    OperationResult op = new OperationResult();
    op.setId(1L);
    op.setMessage("A/B测试创建成功");
    when(campaignMarketingService.createAbTest(any())).thenReturn(op);

    Result<OperationResult> result = controller.createAbTest(request);

    ArgumentCaptor<AbTestRequest> captor = ArgumentCaptor.forClass(AbTestRequest.class);
    verify(campaignMarketingService).createAbTest(captor.capture());
    assertEquals("新测试", captor.getValue().getName());
    assertEquals(50, captor.getValue().getGroupAVisitors());

    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
  }

  // ============ updateAbTest ============

  @Test
  void updateAbTest_有效请求_委托Service并返回操作结果() {
    AbTestRequest request = new AbTestRequest();
    request.setStatus("COMPLETED");
    OperationResult op = new OperationResult();
    op.setId(1L);
    op.setMessage("A/B测试更新成功");
    when(campaignMarketingService.updateAbTest(eq(1L), any())).thenReturn(op);

    Result<OperationResult> result = controller.updateAbTest(1L, request);

    verify(campaignMarketingService).updateAbTest(eq(1L), eq(request));
    assertEquals(0, result.getCode());
    assertEquals(1L, result.getData().getId());
  }

  // ============ effects ============

  @Test
  void effects_默认7天_委托Service并返回EffectResponse() {
    EffectResponse effect = new EffectResponse();
    effect.setTotalGmv(new BigDecimal("10000"));
    effect.setTotalOrders(5);
    when(campaignMarketingService.getMarketingEffects(7)).thenReturn(effect);

    Result<EffectResponse> result = controller.effects(7);

    verify(campaignMarketingService).getMarketingEffects(7);
    assertEquals(0, result.getCode());
    assertEquals(new BigDecimal("10000"), result.getData().getTotalGmv());
    assertEquals(5, result.getData().getTotalOrders());
  }

  @Test
  void effects_使用默认值_委托Service() {
    // given:不传 days 参数时使用默认值 7
    EffectResponse effect = new EffectResponse();
    when(campaignMarketingService.getMarketingEffects(7)).thenReturn(effect);

    // when:模拟默认参数调用
    Result<EffectResponse> result = controller.effects(7);

    // then
    verify(campaignMarketingService).getMarketingEffects(7);
    assertEquals(0, result.getCode());
  }
}

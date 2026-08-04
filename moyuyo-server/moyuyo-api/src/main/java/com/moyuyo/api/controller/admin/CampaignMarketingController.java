package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.campaign.*;
import com.moyuyo.service.admin.CampaignMarketingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理后台 - 营销管理")
@RestController
@RequestMapping("/api/admin/marketing")
@RequiredArgsConstructor
public class CampaignMarketingController {

  private final CampaignMarketingService campaignMarketingService;

  @Operation(summary = "营销活动列表")
  @GetMapping("/campaigns")
  public Result<PageResponse<CampaignResponse>> campaigns(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(campaignMarketingService.listCampaigns(page, size));
  }

  @Operation(summary = "创建活动")
  @PostMapping("/campaigns")
  public Result<OperationResult> createCampaign(@Valid @RequestBody CampaignRequest request) {
    return Result.success(campaignMarketingService.createCampaign(request));
  }

  @Operation(summary = "更新活动")
  @PutMapping("/campaigns/{id}")
  public Result<OperationResult> updateCampaign(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
    return Result.success(campaignMarketingService.updateCampaign(id, request));
  }

  @Operation(summary = "活动详情")
  @GetMapping("/campaigns/{id}")
  public Result<CampaignDetailResponse> campaignDetail(@PathVariable Long id) {
    CampaignDetailResponse data = campaignMarketingService.getCampaignDetail(id);
    if (data == null) {
      return Result.error(404, "活动不存在");
    }
    return Result.success(data);
  }

  @Operation(summary = "删除活动")
  @DeleteMapping("/campaigns/{id}")
  public Result<OperationResult> deleteCampaign(@PathVariable Long id) {
    return Result.success(campaignMarketingService.deleteCampaign(id));
  }

  @Operation(summary = "A/B测试列表")
  @GetMapping("/ab-tests")
  public Result<List<AbTestResponse>> abTests() {
    return Result.success(campaignMarketingService.listAbTests());
  }

  @Operation(summary = "创建A/B测试")
  @PostMapping("/ab-tests")
  public Result<OperationResult> createAbTest(@RequestBody AbTestRequest request) {
    return Result.success(campaignMarketingService.createAbTest(request));
  }

  @Operation(summary = "更新A/B测试")
  @PutMapping("/ab-tests/{id}")
  public Result<OperationResult> updateAbTest(@PathVariable Long id, @RequestBody AbTestRequest request) {
    return Result.success(campaignMarketingService.updateAbTest(id, request));
  }

  @Operation(summary = "营销效果统计")
  @GetMapping("/effects")
  public Result<EffectResponse> effects(@RequestParam(defaultValue = "7") int days) {
    return Result.success(campaignMarketingService.getMarketingEffects(days));
  }
}

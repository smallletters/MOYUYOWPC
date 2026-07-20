package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.CampaignMarketingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "管理后台 - 营销管理")
@RestController
@RequestMapping("/api/admin/marketing")
@RequiredArgsConstructor
public class CampaignMarketingController {

  private final CampaignMarketingService campaignMarketingService;

  @Operation(summary = "营销活动列表")
  @GetMapping("/campaigns")
  public Result<Map<String, Object>> campaigns(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(campaignMarketingService.listCampaigns(page, size));
  }

  @Operation(summary = "创建活动")
  @PostMapping("/campaigns")
  public Result<Map<String, Object>> createCampaign(@RequestBody Map<String, Object> body) {
    return Result.success(campaignMarketingService.createCampaign(body));
  }

  @Operation(summary = "更新活动")
  @PutMapping("/campaigns/{id}")
  public Result<Map<String, Object>> updateCampaign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    return Result.success(campaignMarketingService.updateCampaign(id, body));
  }

  @Operation(summary = "活动详情")
  @GetMapping("/campaigns/{id}")
  public Result<Map<String, Object>> campaignDetail(@PathVariable Long id) {
    return Result.success(campaignMarketingService.getCampaignDetail(id));
  }

  @Operation(summary = "删除活动")
  @DeleteMapping("/campaigns/{id}")
  public Result<Map<String, Object>> deleteCampaign(@PathVariable Long id) {
    return Result.success(campaignMarketingService.deleteCampaign(id));
  }

  @Operation(summary = "A/B测试列表")
  @GetMapping("/ab-tests")
  public Result<?> abTests() {
    return Result.success(campaignMarketingService.listAbTests());
  }

  @Operation(summary = "创建A/B测试")
  @PostMapping("/ab-tests")
  public Result<Map<String, Object>> createAbTest(@RequestBody Map<String, Object> body) {
    return Result.success(campaignMarketingService.createAbTest(body));
  }

  @Operation(summary = "营销效果统计")
  @GetMapping("/effects")
  public Result<Map<String, Object>> effects(@RequestParam(defaultValue = "7") int days) {
    return Result.success(campaignMarketingService.getMarketingEffects(days));
  }
}

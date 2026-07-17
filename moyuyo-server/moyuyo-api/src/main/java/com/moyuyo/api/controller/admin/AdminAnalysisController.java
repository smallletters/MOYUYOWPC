package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 数据分析")
@RestController
@RequestMapping("/api/admin/analysis")
@RequiredArgsConstructor
public class AdminAnalysisController {

  private final AdminAnalysisService adminAnalysisService;

  @Operation(summary = "漏斗分析数据")
  @GetMapping("/funnel")
  public Result<?> funnel() {
    return Result.success(adminAnalysisService.funnel());
  }

  @Operation(summary = "RFM分析")
  @GetMapping("/rfm")
  public Result<?> rfm() {
    return Result.success(adminAnalysisService.rfm());
  }

  @Operation(summary = "搜索分析KPI")
  @GetMapping("/search")
  public Result<Map<String, Object>> search() {
    return Result.success(adminAnalysisService.searchStats());
  }

  @Operation(summary = "流量分析KPI")
  @GetMapping("/traffic")
  public Result<Map<String, Object>> traffic() {
    return Result.success(adminAnalysisService.trafficStats());
  }
}

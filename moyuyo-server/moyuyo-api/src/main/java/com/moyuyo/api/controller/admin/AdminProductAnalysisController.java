package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminProductAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 商品分析")
@RestController
@RequestMapping("/api/admin/product-analysis")
@RequiredArgsConstructor
public class AdminProductAnalysisController {

  private final AdminProductAnalysisService adminProductAnalysisService;

  @Operation(summary = "商品分析KPI")
  @GetMapping("/kpi")
  public Result<Map<String, Object>> kpi() {
    Map<String, Object> data = adminProductAnalysisService.overview();
    return Result.success(data);
  }

  @Operation(summary = "商品分析列表（销量排行）")
  @GetMapping("/list")
  public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "20") int limit) {
    // 返回销量排行数据，支持自定义返回条数
    List<Map<String, Object>> list = adminProductAnalysisService.topSales(limit);
    return Result.success(list);
  }

  @Operation(summary = "商品报表")
  @GetMapping("/report")
  public Result<List<Map<String, Object>>> report(
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate) {
    // 传入日期范围参数进行数据筛选
    List<Map<String, Object>> list = adminProductAnalysisService.report(startDate, endDate);
    return Result.success(list);
  }

  @Operation(summary = "新品表现追踪")
  @GetMapping("/new-products")
  public Result<Map<String, Object>> newProducts(@RequestParam(defaultValue = "7") int days) {
    return Result.success(adminProductAnalysisService.newProductTracking(days));
  }

  @Operation(summary = "滞销商品预警")
  @GetMapping("/slow-moving")
  public Result<List<Map<String, Object>>> slowMoving() {
    return Result.success(adminProductAnalysisService.slowMovingProducts());
  }

  @Operation(summary = "流转率概览")
  @GetMapping("/turnover")
  public Result<Map<String, Object>> turnover() {
    return Result.success(adminProductAnalysisService.turnoverOverview());
  }

  @Operation(summary = "热门搜索词 Top 10")
  @GetMapping("/hot-keywords")
  public Result<List<Map<String, Object>>> hotKeywords() {
    return Result.success(adminProductAnalysisService.hotSearchKeywords());
  }

  @Operation(summary = "评价分析概览")
  @GetMapping("/review-analysis")
  public Result<Map<String, Object>> reviewAnalysis() {
    return Result.success(adminProductAnalysisService.reviewAnalysis());
  }

  @Operation(summary = "高频评价关键词")
  @GetMapping("/review-keywords")
  public Result<List<Map<String, Object>>> reviewKeywords() {
    return Result.success(adminProductAnalysisService.reviewKeywords());
  }

  @Operation(summary = "库存健康度概览")
  @GetMapping("/inventory-health")
  public Result<Map<String, Object>> inventoryHealth() {
    return Result.success(adminProductAnalysisService.inventoryHealth());
  }

  @Operation(summary = "库存周转天数排行")
  @GetMapping("/inventory-turnover")
  public Result<List<Map<String, Object>>> inventoryTurnover() {
    return Result.success(adminProductAnalysisService.inventoryTurnoverRanking());
  }
}

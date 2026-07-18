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

  @Operation(summary = "商品分析列表")
  @GetMapping("/list")
  // TODO: 需要基于 overview/topSales 返回值或创建更详细的查询方法
  public Result<List<Map<String, Object>>> list() {
    List<Map<String, Object>> list = adminProductAnalysisService.topSales(20);
    return Result.success(list);
  }

  @Operation(summary = "商品报表")
  @GetMapping("/report")
  public Result<List<Map<String, Object>>> report(
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate) {
    // 从数据库查询真实商品数据
    List<Map<String, Object>> list = adminProductAnalysisService.report();
    return Result.success(list);
  }
}

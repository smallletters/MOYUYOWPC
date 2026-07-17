package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminSatisfactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 满意度管理")
@RestController
@RequestMapping("/api/admin/satisfaction")
@RequiredArgsConstructor
public class AdminSatisfactionController {

  private final AdminSatisfactionService adminSatisfactionService;

  @Operation(summary = "满意度统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(adminSatisfactionService.stats());
  }

  @Operation(summary = "评价列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(required = false) Integer score,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminSatisfactionService.listAll(score, category, page, size));
  }
}

package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.PriceStrategyEntity;
import com.moyuyo.service.admin.AdminPriceStrategyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 价格管理")
@RestController
@RequestMapping("/api/admin/price")
@RequiredArgsConstructor
public class AdminPriceController {

  private final AdminPriceStrategyService adminPriceStrategyService;

  @Operation(summary = "价格策略列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminPriceStrategyService.listAll(type, enabled, page, size));
  }

  @Operation(summary = "创建策略")
  @PostMapping("/create")
  public Result<Map<String, Object>> create(@RequestBody PriceStrategyEntity body) {
    adminPriceStrategyService.create(body);
    return Result.success(Map.of("id", body.getId(), "message", "策略创建成功"));
  }

  @Operation(summary = "更新策略")
  @PutMapping("/update")
  public Result<Map<String, Object>> update(@RequestBody PriceStrategyEntity body) {
    adminPriceStrategyService.update(body);
    return Result.success(Map.of("id", body.getId(), "message", "策略更新成功"));
  }

  @Operation(summary = "删除策略")
  @DeleteMapping("/{id}")
  public Result<Map<String, Object>> delete(@PathVariable Long id) {
    adminPriceStrategyService.delete(id);
    return Result.success(Map.of("id", id, "message", "策略删除成功"));
  }

  @Operation(summary = "启用/禁用策略")
  @PutMapping("/{id}/toggle")
  public Result<Map<String, Object>> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Boolean enabled = Boolean.valueOf(String.valueOf(body.getOrDefault("enabled", true)));
    adminPriceStrategyService.toggle(id, enabled);
    return Result.success(Map.of("id", id, "enabled", enabled, "message", "策略状态更新成功"));
  }
}

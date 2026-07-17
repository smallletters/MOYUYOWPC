package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.AppVersionEntity;
import com.moyuyo.service.admin.AdminAppVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - App版本管理")
@RestController
@RequestMapping("/api/admin/app-version")
@RequiredArgsConstructor
public class AdminAppVersionController {

  private final AdminAppVersionService adminAppVersionService;

  @Operation(summary = "版本列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(required = false) String appType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminAppVersionService.listAll(appType, page, size));
  }

  @Operation(summary = "创建版本")
  @PostMapping("/create")
  public Result<Map<String, Object>> create(@RequestBody AppVersionEntity body) {
    adminAppVersionService.create(body);
    return Result.success(Map.of("id", body.getId(), "message", "版本创建成功"));
  }

  @Operation(summary = "更新版本")
  @PutMapping("/update")
  public Result<Map<String, Object>> update(@RequestBody AppVersionEntity body) {
    adminAppVersionService.update(body);
    return Result.success(Map.of("id", body.getId(), "message", "版本更新成功"));
  }

  @Operation(summary = "发布版本")
  @PostMapping("/{id}/publish")
  public Result<Map<String, Object>> publish(@PathVariable Long id) {
    adminAppVersionService.publish(id);
    return Result.success(Map.of("id", id, "message", "版本发布成功"));
  }

  @Operation(summary = "回滚版本")
  @PostMapping("/{id}/rollback")
  public Result<Map<String, Object>> rollback(@PathVariable Long id) {
    adminAppVersionService.rollback(id);
    return Result.success(Map.of("id", id, "message", "版本回滚成功"));
  }

  @Operation(summary = "删除版本")
  @DeleteMapping("/{id}")
  public Result<Map<String, Object>> delete(@PathVariable Long id) {
    adminAppVersionService.delete(id);
    return Result.success(Map.of("id", id, "message", "版本删除成功"));
  }
}

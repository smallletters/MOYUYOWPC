package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminPointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 积分管理")
@RestController
@RequestMapping("/api/admin/points")
@RequiredArgsConstructor
public class AdminPointsController {

  private final AdminPointsService adminPointsService;

  @Operation(summary = "积分活动列表")
  @GetMapping("/activities")
  public Result<Map<String, Object>> activities(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    try {
      List<Map<String, Object>> allActivities = adminPointsService.listActivities();
      int total = allActivities.size();
      int fromIndex = (page - 1) * size;
      int toIndex = Math.min(fromIndex + size, total);
      List<Map<String, Object>> pagedRecords;
      if (fromIndex >= total) {
        pagedRecords = List.of();
      } else {
        pagedRecords = allActivities.subList(fromIndex, toIndex);
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("total", (long) total);
      result.put("records", pagedRecords);
      result.put("page", (long) page);
      result.put("size", (long) size);
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("查询积分活动列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "积分活动详情")
  @GetMapping("/activities/{id}")
  public Result<?> detailActivity(@PathVariable String id) {
    try {
      List<Map<String, Object>> activities = adminPointsService.listActivities();
      Map<String, Object> activity = activities.stream()
          .filter(a -> id.equals(a.get("id")))
          .findFirst()
          .orElse(null);
      if (activity == null) {
        return Result.error("活动不存在");
      }
      return Result.success(activity);
    } catch (Exception e) {
      return Result.error("查询活动详情失败: " + e.getMessage());
    }
  }

  @Operation(summary = "创建积分活动")
  @PostMapping("/activities/create")
  public Result<Map<String, Object>> createActivity(@RequestBody Map<String, Object> body) {
    try {
      adminPointsService.createActivity(body);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", body.get("id"));
      result.put("message", "创建成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("创建活动失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新积分活动")
  @PutMapping("/activities/{id}")
  public Result<Map<String, Object>> updateActivity(@PathVariable String id, @RequestBody Map<String, Object> body) {
    try {
      adminPointsService.updateActivity(id, body);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "更新成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("更新活动失败: " + e.getMessage());
    }
  }

  @Operation(summary = "删除积分活动（通过ID删除）")
  @DeleteMapping("/activities/{id}")
  public Result<Map<String, Object>> deleteActivityById(@PathVariable String id) {
    try {
      adminPointsService.deleteActivity(id);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("message", "删除成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("删除活动失败: " + e.getMessage());
    }
  }

  @Operation(summary = "积分流水")
  @GetMapping("/logs")
  public Result<Map<String, Object>> logs(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) Long userId) {
    try {
      return Result.success(adminPointsService.listLogs(page, size, userId));
    } catch (Exception e) {
      return Result.error("查询积分流水失败: " + e.getMessage());
    }
  }

  @Operation(summary = "积分统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    try {
      return Result.success(adminPointsService.getStats());
    } catch (Exception e) {
      return Result.error("查询积分统计失败: " + e.getMessage());
    }
  }

  @Operation(summary = "用户积分查询")
  @GetMapping("/users/{userId}/points")
  public Result<Map<String, Object>> userPoints(@PathVariable Long userId) {
    try {
      return Result.success(adminPointsService.getUserPoints(userId));
    } catch (Exception e) {
      return Result.error("查询用户积分失败: " + e.getMessage());
    }
  }

  @Operation(summary = "手动调整积分")
  @PostMapping("/users/{userId}/adjust")
  public Result<Map<String, Object>> adjustPoints(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
    try {
      int amount = body.get("amount") instanceof Number ? ((Number) body.get("amount")).intValue() : 0;
      String reason = (String) body.getOrDefault("reason", "手动调整");
      adminPointsService.adjustPoints(userId, amount, reason);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("userId", userId);
      result.put("amount", amount);
      result.put("message", "调整成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("调整积分失败: " + e.getMessage());
    }
  }
}

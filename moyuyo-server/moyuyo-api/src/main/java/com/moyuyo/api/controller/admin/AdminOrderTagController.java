package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.ordertag.OrderTagCreateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagUpdateRequest;
import com.moyuyo.common.dto.admin.ordertag.OrderTagVO;
import com.moyuyo.service.admin.AdminOrderTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 订单标签管理")
@RestController
@RequestMapping("/api/admin/order-tags")
@RequiredArgsConstructor
public class AdminOrderTagController {

  private final AdminOrderTagService adminOrderTagService;

  @Operation(summary = "标签列表")
  @GetMapping("/list")
  public Result<List<OrderTagVO>> list() {
    return Result.success(adminOrderTagService.listAll());
  }

  @Operation(summary = "创建标签")
  @PostMapping("/create")
  public Result<OperationResult> create(@Valid @RequestBody OrderTagCreateRequest request) {
    adminOrderTagService.create(request);
    OperationResult result = new OperationResult();
    result.setMessage("创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新标签")
  @PutMapping("/update")
  public Result<OperationResult> update(@Valid @RequestBody OrderTagUpdateRequest request) {
    adminOrderTagService.update(request);
    OperationResult result = new OperationResult();
    result.setId(request.getId());
    result.setMessage("更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除标签")
  @DeleteMapping("/{id}")
  public Result<OperationResult> delete(@PathVariable Long id) {
    adminOrderTagService.delete(id);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("删除成功");
    return Result.success(result);
  }

  @Operation(summary = "给订单打标签")
  @PostMapping("/{orderId}/tags")
  public Result<OperationResult> addOrderTags(@PathVariable Long orderId, @RequestBody Object body) {
    // 兼容两种格式:直接传整数ID列表,或传包装对象 {tags: [1,2,3]}
    List<Long> tagIds = parseTagIds(body);
    adminOrderTagService.setOrderTags(orderId, tagIds);
    OperationResult result = new OperationResult();
    result.setId(orderId);
    result.setMessage("打标成功");
    return Result.success(result);
  }

  @Operation(summary = "获取订单标签")
  @GetMapping("/{orderId}/tags")
  public Result<Map<String, Object>> getOrderTags(@PathVariable Long orderId) {
    // 保留 Map 包装以兼容前端期望的 {orderId, tags} 结构
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("orderId", orderId);
    result.put("tags", adminOrderTagService.getOrderTags(orderId));
    return Result.success(result);
  }

  /** 从请求体中解析标签ID列表,兼容直接传数组或 {tags: [...]} 两种格式 */
  @SuppressWarnings("unchecked")
  private static List<Long> parseTagIds(Object body) {
    if (body instanceof List<?> rawList) {
      return rawList.stream()
          .map(AdminOrderTagController::parseLongId)
          .filter(v -> v != null)
          .toList();
    }
    if (body instanceof Map<?, ?> bodyMap) {
      Object tagsObj = bodyMap.get("tags");
      if (tagsObj instanceof List<?> tagsList) {
        return tagsList.stream()
            .map(AdminOrderTagController::parseLongId)
            .filter(v -> v != null)
            .toList();
      }
    }
    return new java.util.ArrayList<>();
  }

  /** 将 Number 或 String 统一解析为 Long(雪花ID序列化为字符串后需兼容) */
  private static Long parseLongId(Object item) {
    if (item instanceof Number) {
      return ((Number) item).longValue();
    }
    if (item instanceof String) {
      try {
        return Long.valueOf((String) item);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}

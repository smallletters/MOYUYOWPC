package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminOrderTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public Result<Map<String, Object>> list() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", (long) adminOrderTagService.listAll().size());
        result.put("records", adminOrderTagService.listAll());
        return Result.success(result);
    }

    @Operation(summary = "创建标签")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        adminOrderTagService.create(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新标签")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        adminOrderTagService.update(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminOrderTagService.delete(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "给订单打标签")
    @PostMapping("/{orderId}/tags")
    public Result<Map<String, Object>> addOrderTags(@PathVariable Long orderId, @RequestBody List<Long> tagIds) {
        adminOrderTagService.setOrderTags(orderId, tagIds);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("tagCount", tagIds != null ? tagIds.size() : 0);
        result.put("message", "打标成功");
        return Result.success(result);
    }

    @Operation(summary = "获取订单标签")
    @GetMapping("/{orderId}/tags")
    public Result<Map<String, Object>> getOrderTags(@PathVariable Long orderId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("tags", adminOrderTagService.getOrderTags(orderId));
        return Result.success(result);
    }
}

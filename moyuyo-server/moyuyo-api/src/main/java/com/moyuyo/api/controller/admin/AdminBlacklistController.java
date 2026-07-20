package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 黑名单管理")
@RestController
@RequestMapping("/api/admin/blacklist")
@RequiredArgsConstructor
public class AdminBlacklistController {

    private final AdminBlacklistService adminBlacklistService;

    @Operation(summary = "按类型查询黑名单")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminBlacklistService.listAll(type, page, size));
    }

    @Operation(summary = "添加黑名单")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        adminBlacklistService.create(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "添加成功");
        return Result.success(result);
    }

    @Operation(summary = "批量添加黑名单")
    @PostMapping("/batch-create")
    public Result<Map<String, Object>> batchCreate(@RequestBody List<Map<String, Object>> items) {
        adminBlacklistService.batchCreate(items);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("count", items != null ? items.size() : 0);
        result.put("message", "批量添加成功");
        return Result.success(result);
    }

    @Operation(summary = "移除黑名单")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminBlacklistService.delete(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "移除成功");
        return Result.success(result);
    }

    @Operation(summary = "更新黑名单")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        adminBlacklistService.update(id, body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "更新成功");
        return Result.success(result);
    }
}

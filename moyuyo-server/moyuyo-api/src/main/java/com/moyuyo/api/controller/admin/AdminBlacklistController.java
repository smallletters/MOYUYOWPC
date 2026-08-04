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
        // 前置参数校验：type/value 为 NOT NULL 字段，空 body 时直接返回友好错误而非数据库异常
        Object typeObj = body.get("type");
        if (typeObj == null || typeObj.toString().trim().isEmpty()) {
            return Result.error(400, "黑名单类型不能为空");
        }
        Object valueObj = body.get("value");
        if (valueObj == null) {
            valueObj = body.get("target");
        }
        if (valueObj == null || valueObj.toString().trim().isEmpty()) {
            return Result.error(400, "黑名单值不能为空");
        }
        var entity = adminBlacklistService.create(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("message", "添加成功");
        return Result.success(result);
    }

    @Operation(summary = "批量添加黑名单")
    @PostMapping("/batch-create")
    public Result<Map<String, Object>> batchCreate(@RequestBody List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return Result.error(400, "批量添加列表不能为空");
        }
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            Object t = item.get("type");
            if (t == null || t.toString().trim().isEmpty()) {
                return Result.error(400, "第" + (i + 1) + "条记录：类型不能为空");
            }
            Object v = item.get("value");
            if (v == null) v = item.get("target");
            if (v == null || v.toString().trim().isEmpty()) {
                return Result.error(400, "第" + (i + 1) + "条记录：值不能为空");
            }
        }
        adminBlacklistService.batchCreate(items);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("count", items.size());
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

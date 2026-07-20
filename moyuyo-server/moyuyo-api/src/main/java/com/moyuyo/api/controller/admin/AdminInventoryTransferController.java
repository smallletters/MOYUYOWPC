package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminInventoryTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 库存调拨管理")
@RestController
@RequestMapping("/api/admin/inventory-transfer")
@RequiredArgsConstructor
public class AdminInventoryTransferController {

    private final AdminInventoryTransferService adminInventoryTransferService;

    @Operation(summary = "调拨记录列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status) {
        return Result.success(adminInventoryTransferService.listAll(page, size, status));
    }

    @Operation(summary = "创建调拨")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        adminInventoryTransferService.create(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id) {
        adminInventoryTransferService.approve(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "审批通过成功");
        return Result.success(result);
    }

    @Operation(summary = "驳回调拨")
    @PutMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id) {
        adminInventoryTransferService.reject(id, null);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "已驳回");
        return Result.success(result);
    }

    @Operation(summary = "确认完成")
    @PutMapping("/{id}/complete")
    public Result<Map<String, Object>> complete(@PathVariable Long id) {
        adminInventoryTransferService.complete(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "已确认完成");
        return Result.success(result);
    }
}

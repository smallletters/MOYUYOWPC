package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminProductApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 商品审核")
@RestController
@RequestMapping("/api/admin/product-approval")
@RequiredArgsConstructor
public class AdminProductApprovalController {

    private final AdminProductApprovalService adminProductApprovalService;

    @Operation(summary = "审核记录列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String status) {
        return Result.success(adminProductApprovalService.listAll(page, size, status));
    }

    @Operation(summary = "审核详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(adminProductApprovalService.getById(id));
    }

    @Operation(summary = "通过审核")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        adminProductApprovalService.approve(id, null);
        return Result.success();
    }

    @Operation(summary = "驳回审核")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = (String) body.getOrDefault("reason", "");
        adminProductApprovalService.reject(id, null, reason);
        return Result.success();
    }

    @Operation(summary = "标记加急")
    @PutMapping("/{id}/urgent")
    public Result<Void> urgent(@PathVariable Long id) {
        adminProductApprovalService.setUrgent(id);
        return Result.success();
    }
}

package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminContentReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 内容审核")
@RestController
@RequestMapping("/api/admin/content-review")
@RequiredArgsConstructor
public class AdminContentReviewController {

    private final AdminContentReviewService adminContentReviewService;

    @Operation(summary = "内容审核列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String contentType,
        @RequestParam(required = false) String status) {
        return Result.success(adminContentReviewService.listAll(page, size, contentType, status));
    }

    @Operation(summary = "审核详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(adminContentReviewService.getById(id));
    }

    @Operation(summary = "审核通过")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        adminContentReviewService.approve(id, null);
        return Result.success();
    }

    @Operation(summary = "审核驳回")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = (String) body.getOrDefault("reason", "");
        String comment = (String) body.getOrDefault("comment", "");
        adminContentReviewService.reject(id, null, reason, comment);
        return Result.success();
    }
}

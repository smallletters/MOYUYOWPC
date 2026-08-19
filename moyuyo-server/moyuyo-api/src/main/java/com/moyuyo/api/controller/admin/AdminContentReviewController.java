package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.admin.AdminContentReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String mode) {
        if (page < 1 || size < 1 || size > 100) {
            return Result.error(400, "分页参数无效");
        }
        if (mode != null && !mode.isBlank()
                && !List.of("auto", "auto_manual", "manual").contains(mode)) {
            return Result.error(400, "审核模式无效");
        }
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
        // 使用当前登录用户ID作为审核人，避免审计日志为null
        Long reviewerId = UserContextHolder.getUserId();
        adminContentReviewService.approve(id, reviewerId);
        return Result.success();
    }

    @Operation(summary = "审核驳回")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (body == null || body.get("reason") == null || body.get("reason").toString().isBlank()) {
            return Result.error(400, "驳回原因不能为空");
        }
        String reason = body.get("reason").toString();
        String comment = body.get("comment") == null ? "" : body.get("comment").toString();
        Long reviewerId = UserContextHolder.getUserId();
        adminContentReviewService.reject(id, reviewerId, reason, comment);
        return Result.success();
    }

    @Operation(summary = "隐藏内容")
    @PutMapping("/{id}/hide")
    public Result<Void> hide(@PathVariable Long id) {
        adminContentReviewService.hide(id);
        return Result.success();
    }

    @Operation(summary = "删除内容")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminContentReviewService.deleteContent(id);
        return Result.success();
    }

    @Operation(summary = "封禁内容")
    @PutMapping("/{id}/ban")
    public Result<Void> ban(@PathVariable Long id) {
        adminContentReviewService.ban(id);
        return Result.success();
    }

    @Operation(summary = "审核统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(adminContentReviewService.getStats());
    }

    @Operation(summary = "审核趋势数据")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(adminContentReviewService.getTrend(days));
    }
}

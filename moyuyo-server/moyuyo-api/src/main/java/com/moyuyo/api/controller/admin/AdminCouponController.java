package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 优惠券管理")
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @Operation(summary = "优惠券列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "15") int size) {
        try {
            Map<String, Object> result = adminCouponService.listPage(page, size);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询优惠券列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "优惠券详情")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        try {
            return Result.success(adminCouponService.getById(id));
        } catch (Exception e) {
            return Result.error("查询优惠券详情失败: " + e.getMessage());
        }
    }

    @Operation(summary = "创建优惠券")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            // 前置参数校验：name 为 NOT NULL 字段，空 body 时直接返回友好错误而非数据库异常
            Object nameObj = body.get("name");
            if (nameObj == null || nameObj.toString().trim().isEmpty()) {
                return Result.error("优惠券名称不能为空");
            }
            adminCouponService.create(body);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", body.get("id"));
            result.put("message", "创建成功");
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("创建优惠券失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新优惠券")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        try {
            adminCouponService.update(body);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", body.get("id"));
            result.put("message", "更新成功");
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("更新优惠券失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除优惠券")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        try {
            adminCouponService.delete(id);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", id);
            result.put("message", "删除成功");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("删除优惠券失败: " + e.getMessage());
        }
    }

    @Operation(summary = "优惠券统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        try {
            return Result.success(adminCouponService.getStats());
        } catch (Exception e) {
            return Result.error("查询优惠券统计失败: " + e.getMessage());
        }
    }
}

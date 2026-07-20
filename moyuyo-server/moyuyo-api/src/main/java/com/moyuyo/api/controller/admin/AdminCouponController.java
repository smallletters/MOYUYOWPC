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
    public Result<Map<String, Object>> list() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", (long) adminCouponService.listAll().size());
        result.put("records", adminCouponService.listAll());
        return Result.success(result);
    }

    @Operation(summary = "创建优惠券")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        adminCouponService.create(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新优惠券")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        adminCouponService.update(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除优惠券")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminCouponService.delete(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "优惠券统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(adminCouponService.getStats());
    }
}

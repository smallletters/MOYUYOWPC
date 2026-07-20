package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminFlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 秒杀活动管理")
@RestController
@RequestMapping("/api/admin/flash-sales")
@RequiredArgsConstructor
public class AdminFlashSaleController {

    private final AdminFlashSaleService adminFlashSaleService;

    @Operation(summary = "秒杀活动列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", (long) adminFlashSaleService.listAll().size());
        result.put("records", adminFlashSaleService.listAll());
        return Result.success(result);
    }

    @Operation(summary = "创建秒杀活动")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        adminFlashSaleService.create(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新秒杀活动")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        adminFlashSaleService.update(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除秒杀活动")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminFlashSaleService.delete(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "修改秒杀活动状态")
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam Boolean active) {
        adminFlashSaleService.updateStatus(id, active != null ? active.toString() : "false");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("active", active);
        result.put("message", "状态更新成功");
        return Result.success(result);
    }
}

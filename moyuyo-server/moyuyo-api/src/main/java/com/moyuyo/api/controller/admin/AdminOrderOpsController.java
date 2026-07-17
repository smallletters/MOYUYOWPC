package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 订单运营")
@RestController
@RequestMapping("/api/admin/order-ops")
@RequiredArgsConstructor
public class AdminOrderOpsController {

    @Operation(summary = "订单导出列表")
    @GetMapping("/export")
    public Result<List<Map<String, Object>>> exportList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("taskNo", "EXP-202607" + String.format("%04d", i));
            item.put("fileName", "订单导出_" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "_" + i + ".xlsx");
            item.put("status", i % 4 == 0 ? "FAILED" : i % 3 == 0 ? "PROCESSING" : "COMPLETED");
            item.put("totalOrders", 100 + i * 50);
            item.put("createTime", LocalDateTime.now().minusHours(i));
            item.put("completeTime", i % 3 == 0 ? null : LocalDateTime.now().minusHours(i - 1));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "创建导出任务")
    @PostMapping("/export")
    public Result<Map<String, Object>> createExport(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", "EXP-202607" + String.format("%04d", new Random().nextInt(1000)));
        result.put("message", "导出任务创建成功");
        return Result.success(result);
    }

    @Operation(summary = "订单拦截列表")
    @GetMapping("/intercept")
    public Result<List<Map<String, Object>>> interceptList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("orderNo", "MOY202607" + String.format("%04d", i));
            item.put("userName", "用户" + i);
            item.put("amount", new BigDecimal(100 + i * 20));
            item.put("reason", i % 3 == 0 ? "疑似刷单" : i % 2 == 0 ? "地址异常" : "重复订单");
            item.put("status", i % 4 == 0 ? "INTERCEPTED" : "PENDING_REVIEW");
            item.put("createTime", LocalDateTime.now().minusHours(i * 2));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "拦截订单")
    @PostMapping("/{id}/intercept")
    public Result<Map<String, Object>> intercept(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("reason", body.get("reason"));
        result.put("message", "订单已拦截");
        return Result.success(result);
    }

    @Operation(summary = "放行订单")
    @PostMapping("/{id}/release")
    public Result<Map<String, Object>> release(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "订单已放行");
        return Result.success(result);
    }

    @Operation(summary = "订单监控")
    @GetMapping("/monitor")
    public Result<Map<String, Object>> monitor() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalOrders", 12580);
        data.put("pendingPayment", 56);
        data.put("pendingShip", 128);
        data.put("shipped", 342);
        data.put("completed", 11500);
        data.put("cancelled", 554);
        data.put("abnormalOrders", 5);

        // 实时订单流
        List<Map<String, Object>> realtime = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderNo", "MOY202607" + String.format("%04d", 100 + i));
            item.put("amount", new BigDecimal(50 + i * 30));
            item.put("status", "PENDING_SHIP");
            item.put("createTime", LocalDateTime.now().minusMinutes(i * 2));
            realtime.add(item);
        }
        data.put("realtimeOrders", realtime);
        return Result.success(data);
    }

    @Operation(summary = "订单改价列表")
    @GetMapping("/price-modify")
    public Result<List<Map<String, Object>>> priceModifyList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("orderNo", "MOY202607" + String.format("%04d", i));
            item.put("originalAmount", 200 + i * 10);
            item.put("modifiedAmount", 180 + i * 10);
            item.put("diffAmount", -20);
            item.put("reason", "老客户优惠");
            item.put("operator", "客服小王");
            item.put("createTime", LocalDateTime.now().minusHours(i * 3));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "修改订单价格")
    @PutMapping("/{id}/price")
    public Result<Map<String, Object>> modifyPrice(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("newPrice", body.get("price"));
        result.put("message", "改价成功");
        return Result.success(result);
    }

    @Operation(summary = "订单打印列表")
    @GetMapping("/print")
    public Result<List<Map<String, Object>>> printList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("orderNo", "MOY202607" + String.format("%04d", i));
            item.put("userName", "用户" + i);
            item.put("productName", "商品" + i);
            item.put("address", "广东省深圳市南山区科技园" + i + "栋");
            item.put("printStatus", i % 3 == 0 ? "PRINTED" : "UNPRINTED");
            item.put("printCount", i % 3 == 0 ? 1 : 0);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "打印订单")
    @PostMapping("/{id}/print")
    public Result<Map<String, Object>> print(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "打印任务已提交");
        return Result.success(result);
    }
}

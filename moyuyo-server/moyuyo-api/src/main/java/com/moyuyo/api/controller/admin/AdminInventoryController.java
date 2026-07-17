package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 库存管理")
@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "库存概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(inventoryService.getInventoryOverview());
    }

    @Operation(summary = "预警列表")
    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts() {
        return Result.success(inventoryService.getAlertList());
    }

    @Operation(summary = "商品库存列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("sku", "SKU-INV-" + String.format("%04d", i));
            item.put("name", "商品库存" + i);
            item.put("category", i % 2 == 0 ? "食品" : "用品");
            item.put("stock", (int) (Math.random() * 500));
            item.put("lockedStock", (int) (Math.random() * 20));
            item.put("availableStock", (int) (Math.random() * 480));
            item.put("safetyStock", 20);
            item.put("price", new BigDecimal(30 + Math.random() * 300));
            item.put("lastCheckTime", LocalDateTime.now().minusDays((long) (Math.random() * 7)));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "更新库存")
    @PutMapping("/{id}/stock")
    public Result<Map<String, Object>> updateStock(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("stock", body.get("stock"));
        result.put("message", "库存更新成功");
        return Result.success(result);
    }

    @Operation(summary = "新增盘点")
    @PostMapping("/check")
    public Result<Map<String, Object>> createCheck(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("checkNo", "CK" + System.currentTimeMillis());
        result.put("message", "盘点任务创建成功");
        return Result.success(result);
    }
}

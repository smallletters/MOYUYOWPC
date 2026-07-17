package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
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

    @Operation(summary = "库存概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalSku", 12580);
        data.put("lowStockCount", 23);
        data.put("expiringBatchCount", 5);
        data.put("inTransitCount", 8);
        data.put("inTransitQuantity", 1200);

        // 低库存预警明细
        List<Map<String, Object>> lowStock = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sku", "SKU-LS-" + i);
            item.put("name", "低库存商品" + i);
            item.put("stock", i * 2);
            item.put("warningLevel", i == 1 ? "CRITICAL" : "WARNING");
            lowStock.add(item);
        }
        data.put("lowStockAlerts", lowStock);

        // 临期批次
        List<Map<String, Object>> expiring = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("batchNo", "BATCH-2026" + String.format("%02d", i));
            item.put("productName", "临期商品" + i);
            item.put("quantity", 100 + i * 50);
            item.put("expiryDate", LocalDateTime.now().plusDays(i * 3));
            expiring.add(item);
        }
        data.put("expiringBatches", expiring);

        return Result.success(data);
    }

    @Operation(summary = "预警列表")
    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] types = {"LOW_STOCK", "EXPIRING", "OVERDUE"};
        String[] levels = {"CRITICAL", "WARNING", "INFO"};
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("type", types[i % 3]);
            item.put("level", levels[i % 3]);
            item.put("sku", "SKU-ALT-" + String.format("%04d", i));
            item.put("productName", "预警商品" + i);
            item.put("currentStock", (int) (Math.random() * 50));
            item.put("threshold", 20);
            item.put("message", types[i % 3] == "LOW_STOCK" ? "库存低于安全值" : types[i % 3] == "EXPIRING" ? "商品即将过期" : "已超过保质期");
            item.put("createTime", LocalDateTime.now().minusHours(i * 3));
            list.add(item);
        }
        return Result.success(list);
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

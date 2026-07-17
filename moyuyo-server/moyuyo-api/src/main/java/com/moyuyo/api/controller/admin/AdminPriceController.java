package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Tag(name = "管理后台 - 价格管理")
@RestController
@RequestMapping("/api/admin/price")
@RequiredArgsConstructor
public class AdminPriceController {

    @Operation(summary = "价格列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] productNames = {"无线蓝牙耳机", "不锈钢保温杯", "纯棉T恤", "智能手环", "便携充电宝"};
        String[] skus = {"SKU-BT-001", "SKU-CUP-002", "SKU-TEE-003", "SKU-BAND-004", "SKU-PB-005"};
        BigDecimal[] originalPrices = {new BigDecimal("199.00"), new BigDecimal("89.00"), new BigDecimal("129.00"), new BigDecimal("299.00"), new BigDecimal("99.00")};
        BigDecimal[] salePrices = {new BigDecimal("159.00"), new BigDecimal("69.00"), new BigDecimal("99.00"), new BigDecimal("249.00"), new BigDecimal("79.00")};
        BigDecimal[] costPrices = {new BigDecimal("80.00"), new BigDecimal("35.00"), new BigDecimal("50.00"), new BigDecimal("130.00"), new BigDecimal("40.00")};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("productName", productNames[i]);
            item.put("sku", skus[i]);
            item.put("originalPrice", originalPrices[i]);
            item.put("salePrice", salePrices[i]);
            item.put("costPrice", costPrices[i]);
            // 毛利率 = (销售价 - 成本价) / 销售价
            BigDecimal grossMargin = salePrices[i].subtract(costPrices[i])
                    .divide(salePrices[i], 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            item.put("grossMargin", grossMargin);
            item.put("updateTime", LocalDateTime.now().minusDays(i * 2));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "价格历史")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam Long productId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] adjustTypes = {"涨价", "降价", "涨价", "降价", "涨价"};
        BigDecimal[] originalPrices = {new BigDecimal("149.00"), new BigDecimal("159.00"), new BigDecimal("99.00"), new BigDecimal("79.00"), new BigDecimal("229.00")};
        BigDecimal[] adjustedPrices = {new BigDecimal("169.00"), new BigDecimal("139.00"), new BigDecimal("129.00"), new BigDecimal("69.00"), new BigDecimal("259.00")};
        String[] operators = {"张三", "李四", "张三", "王五", "张三"};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("productName", "商品-" + productId);
            item.put("sku", "SKU-" + String.format("%04d", productId));
            item.put("originalPrice", originalPrices[i]);
            item.put("adjustedPrice", adjustedPrices[i]);
            item.put("adjustType", adjustTypes[i]);
            item.put("operator", operators[i]);
            item.put("adjustTime", LocalDateTime.now().minusDays(i * 3).minusHours(i));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "更新价格")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("oldPrice", new BigDecimal("159.00"));
        result.put("newPrice", body.get("salePrice"));
        result.put("operator", "当前用户");
        result.put("updateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("message", "价格更新成功");
        return Result.success(result);
    }

    @Operation(summary = "批量调价")
    @PostMapping("/batch-update")
    public Result<Map<String, Object>> batchUpdate(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("affectedCount", 5);
        result.put("adjustType", body.get("adjustType"));
        result.put("adjustValue", body.get("adjustValue"));
        result.put("operator", "当前用户");
        result.put("updateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("message", "批量调价成功");
        return Result.success(result);
    }
}

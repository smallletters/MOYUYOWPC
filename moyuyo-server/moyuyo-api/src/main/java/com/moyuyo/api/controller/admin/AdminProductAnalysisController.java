package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Tag(name = "管理后台 - 商品分析")
@RestController
@RequestMapping("/api/admin/product-analysis")
@RequiredArgsConstructor
public class AdminProductAnalysisController {

    @Operation(summary = "商品分析KPI")
    @GetMapping("/kpi")
    public Result<Map<String, Object>> kpi() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalProductCount", 12580);
        data.put("activeProductCount", 8932);
        data.put("totalPageViews", 358000);
        data.put("totalSales", 28650);
        return Result.success(data);
    }

    @Operation(summary = "商品分析列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] productNames = {"无线蓝牙耳机", "不锈钢保温杯", "纯棉T恤", "智能手环", "便携充电宝", "运动跑鞋", "机械键盘", "降噪耳罩"};
        for (int i = 0; i < 8; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("productName", productNames[i]);
            item.put("pageViews", 5000 + (int) (Math.random() * 20000));
            item.put("favorites", 100 + (int) (Math.random() * 1000));
            item.put("cartAdds", 200 + (int) (Math.random() * 1500));
            item.put("sales", 50 + (int) (Math.random() * 500));
            item.put("revenue", new BigDecimal(5000 + (int) (Math.random() * 50000)));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "商品报表")
    @GetMapping("/report")
    public Result<List<Map<String, Object>>> report(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] productNames = {"无线蓝牙耳机", "不锈钢保温杯", "纯棉T恤", "智能手环", "便携充电宝"};
        String[] skus = {"SKU-BT-001", "SKU-CUP-002", "SKU-TEE-003", "SKU-BAND-004", "SKU-PB-005"};
        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("productName", productNames[i]);
            item.put("sku", skus[i]);
            item.put("sales", 100 + (int) (Math.random() * 500));
            item.put("revenue", new BigDecimal(10000 + (int) (Math.random() * 50000)));
            item.put("profit", new BigDecimal(5000 + (int) (Math.random() * 20000)));
            BigDecimal revenue = (BigDecimal) item.get("revenue");
            BigDecimal profit = (BigDecimal) item.get("profit");
            BigDecimal profitRate = profit.divide(revenue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            item.put("profitRate", profitRate);
            list.add(item);
        }
        return Result.success(list);
    }

}

package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 物流管理")
@RestController
@RequestMapping("/api/admin/logistics")
@RequiredArgsConstructor
public class AdminLogisticsController {

    @Operation(summary = "仓库列表")
    @GetMapping("/warehouses")
    public Result<List<Map<String, Object>>> warehouses() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] types = {"SELF", "THIRD_PARTY", "SELF", "THIRD_PARTY", "SELF"};
        String[] cities = {"上海", "广州", "成都", "武汉", "天津"};
        String[] managers = {"张三", "李四", "王五", "赵六", "孙七"};
        String[] statuses = {"ACTIVE", "ACTIVE", "ACTIVE", "INACTIVE", "ACTIVE"};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", types[i - 1].equals("SELF") ? "自营仓" + i : "第三方仓" + i);
            item.put("type", types[i - 1]);
            item.put("city", cities[i - 1]);
            item.put("area", 500 + i * 300);
            item.put("manager", managers[i - 1]);
            item.put("status", statuses[i - 1]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "新增仓库")
    @PostMapping("/warehouses")
    public Result<Map<String, Object>> createWarehouse(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("message", "仓库创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新仓库")
    @PutMapping("/warehouses/{id}")
    public Result<Map<String, Object>> updateWarehouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "仓库更新成功");
        return Result.success(result);
    }

    @Operation(summary = "海外仓列表")
    @GetMapping("/overseas")
    public Result<List<Map<String, Object>>> overseasWarehouses() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"美国洛杉矶仓", "美国", "45", "12800", "72"},
                {"德国汉堡仓", "德国", "32", "9600", "65"},
                {"日本东京仓", "日本", "28", "7500", "58"},
                {"英国伦敦仓", "英国", "22", "6200", "55"},
                {"澳大利亚悉尼仓", "澳大利亚", "18", "4800", "48"},
        };
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", data[i - 1][0]);
            item.put("country", data[i - 1][1]);
            item.put("skuCount", Integer.parseInt(data[i - 1][2]));
            item.put("totalStock", Integer.parseInt(data[i - 1][3]));
            item.put("usageRate", Integer.parseInt(data[i - 1][4]));
            item.put("status", i <= 3 ? "ACTIVE" : "MAINTENANCE");
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "合包管理列表")
    @GetMapping("/merge-packages")
    public Result<List<Map<String, Object>>> mergePackages() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] statuses = {"PENDING", "PROCESSING", "COMPLETED", "PENDING", "COMPLETED"};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("mergeNo", "MG" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%04d", i));
            item.put("orderCount", 2 + i * 2);
            item.put("packageCount", 1 + i);
            item.put("totalWeight", new BigDecimal("2." + (i * 3)));
            item.put("status", statuses[i - 1]);
            item.put("createTime", LocalDateTime.now().minusDays(i));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "分包裹列表")
    @GetMapping("/split-packages")
    public Result<List<Map<String, Object>>> splitPackages() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] statuses = {"PENDING", "PROCESSING", "COMPLETED", "PENDING", "COMPLETED"};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("orderNo", "MOY202607" + String.format("%04d", i));
            item.put("productCount", 3 + i * 2);
            item.put("splitCount", i + 1);
            item.put("totalWeight", new BigDecimal("3." + (i * 2)));
            item.put("status", statuses[i - 1]);
            item.put("createTime", LocalDateTime.now().minusDays(i));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "承运商对比列表")
    @GetMapping("/carriers")
    public Result<List<Map<String, Object>>> carriers() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"顺丰速运", "航空", "2.5", "20.0", "12.0", "98.5"},
                {"中通快递", "陆运", "3.5", "10.0", "6.0", "96.0"},
                {"圆通速递", "陆运", "3.8", "8.0", "5.0", "95.2"},
                {"韵达快递", "陆运", "4.0", "7.0", "4.5", "94.8"},
                {"极兔速递", "混合", "3.2", "12.0", "7.0", "97.1"},
        };
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", data[i - 1][0]);
            item.put("transportMode", data[i - 1][1]);
            item.put("avgDeliveryDays", new BigDecimal(data[i - 1][2]));
            item.put("firstWeightPrice", new BigDecimal(data[i - 1][3]));
            item.put("renewWeightPrice", new BigDecimal(data[i - 1][4]));
            item.put("praiseRate", new BigDecimal(data[i - 1][5]));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "清关管理列表")
    @GetMapping("/clearance")
    public Result<List<Map<String, Object>>> clearance() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] statuses = {"CLEARED", "INSPECTING", "PENDING", "REJECTED", "CLEARED"};
        String[] products = {"电子产品", "服装", "食品", "化妆品", "玩具"};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("declarationNo", "DEC" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%04d", i));
            item.put("orderNo", "MOY202607" + String.format("%04d", i));
            item.put("productName", products[i - 1]);
            item.put("status", statuses[i - 1]);
            item.put("declareTime", LocalDateTime.now().minusDays(i));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "海关管理列表")
    @GetMapping("/customs")
    public Result<List<Map<String, Object>>> customs() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"HS84713000", "笔记本电脑", "13.0", "3C认证"},
                {"HS62046200", "棉质长裤", "8.0", "商检"},
                {"HS21039090", "调味料", "15.0", "食品卫生许可"},
                {"HS33049900", "护肤品", "10.0", "化妆品备案"},
                {"HS95030010", "电动玩具", "12.0", "3C认证"},
        };
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("hsCode", data[i - 1][0]);
            item.put("productName", data[i - 1][1]);
            item.put("taxRate", new BigDecimal(data[i - 1][2]));
            item.put("supervisionConditions", data[i - 1][3]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "发货策略列表")
    @GetMapping("/shipping-strategies")
    public Result<List<Map<String, Object>>> shippingStrategies() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] names = {"标准发货策略", "急速达策略", "经济配送策略", "海外直邮策略", "大件物流策略"};
        String[] regions = {"全国", "华东/华南", "偏远地区", "海外", "全国大件"};
        String[] methods = {"快递配送", "同城急送", "陆运配送", "国际快递", "专线物流"};
        String[] rules = {"满99免运费", "固定运费15元", "按重量计费", "按体积计费", "按件计费"};
        String[] statuses = {"ACTIVE", "ACTIVE", "ACTIVE", "INACTIVE", "ACTIVE"};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", names[i - 1]);
            item.put("region", regions[i - 1]);
            item.put("method", methods[i - 1]);
            item.put("rule", rules[i - 1]);
            item.put("status", statuses[i - 1]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "同步海关数据")
    @PostMapping("/{id}/customs/sync")
    public Result<Map<String, Object>> syncCustoms(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("syncTime", LocalDateTime.now());
        result.put("message", "海关数据同步成功");
        return Result.success(result);
    }
}

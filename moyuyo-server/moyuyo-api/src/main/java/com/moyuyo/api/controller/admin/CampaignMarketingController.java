package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 营销管理")
@RestController
@RequestMapping("/api/admin/marketing")
@RequiredArgsConstructor
public class CampaignMarketingController {

    @Operation(summary = "营销活动列表")
    @GetMapping("/campaigns")
    public Result<List<Map<String, Object>>> campaigns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"夏日宠物狂欢节", "ACTIVE", "限时秒杀全场6折起", "2026-07-01", "2026-07-31"},
                {"满减特惠周", "UPCOMING", "满200减30，满300减50", "2026-08-01", "2026-08-07"},
                {"新人专属优惠券", "ACTIVE", "新人注册即享100元礼包", "2026-06-01", "2026-12-31"},
                {"限时折扣清仓", "ENDED", "换季清仓5折起", "2026-05-01", "2026-05-31"},
                {"会员日专享", "UPCOMING", "会员双倍积分+专属折扣", "2026-08-15", "2026-08-18"},
        };
        for (String[] c : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", new Random().nextInt(1000));
            item.put("name", c[0]);
            item.put("status", c[1]);
            item.put("description", c[2]);
            item.put("startDate", c[3]);
            item.put("endDate", c[4]);
            item.put("participants", (int) (Math.random() * 500));
            item.put("gmv", new BigDecimal(Math.random() * 50000));
            item.put("budget", new BigDecimal(Math.random() * 10000));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "创建活动")
    @PostMapping("/campaigns")
    public Result<Map<String, Object>> createCampaign(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("name", body.get("name"));
        result.put("message", "活动创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新活动")
    @PutMapping("/campaigns/{id}")
    public Result<Map<String, Object>> updateCampaign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", body.get("name"));
        result.put("message", "活动更新成功");
        return Result.success(result);
    }

    @Operation(summary = "活动详情")
    @GetMapping("/campaigns/{id}")
    public Result<Map<String, Object>> campaignDetail(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("name", "夏日宠物狂欢节");
        data.put("status", "ACTIVE");
        data.put("description", "限时秒杀全场6折起");
        data.put("startDate", "2026-07-01");
        data.put("endDate", "2026-07-31");
        data.put("participants", 356);
        data.put("gmv", 32800);
        data.put("budget", 5000);
        data.put("cost", 3200);

        // 活动效果数据
        Map<String, Object> effects = new LinkedHashMap<>();
        effects.put("orderIncrease", 25.6);
        effects.put("conversionIncrease", 8.3);
        effects.put("avgOrderValue", 168.5);
        effects.put("roi", 6.8);
        data.put("effects", effects);
        return Result.success(data);
    }

    @Operation(summary = "A/B测试列表")
    @GetMapping("/ab-tests")
    public Result<List<Map<String, Object>>> abTests() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"首页Banner风格测试", "RUNNING", "A：大促风格 / B：清新风格", "2300", "2350", "3.2", "3.8"},
                {"商品详情页布局测试", "RUNNING", "A：标准布局 / B：视频优先", "1800", "1820", "4.1", "4.5"},
                {"优惠券面额测试", "COMPLETED", "A：满100减10 / B：满200减30", "5000", "4800", "5.0", "6.2"},
        };
        for (String[] t : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", new Random().nextInt(1000));
            item.put("name", t[0]);
            item.put("status", t[1]);
            item.put("description", t[2]);
            item.put("groupAVisitors", new Integer(t[3]));
            item.put("groupBVisitors", new Integer(t[4]));
            item.put("groupAConvRate", new BigDecimal(t[5]));
            item.put("groupBConvRate", new BigDecimal(t[6]));
            item.put("startTime", LocalDateTime.now().minusDays(7));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "创建A/B测试")
    @PostMapping("/ab-tests")
    public Result<Map<String, Object>> createAbTest(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("name", body.get("name"));
        result.put("message", "A/B测试创建成功");
        return Result.success(result);
    }

    @Operation(summary = "营销效果统计")
    @GetMapping("/effects")
    public Result<Map<String, Object>> effects(
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalGmv", 358000);
        data.put("campaignGmv", 125000);
        data.put("campaignRatio", 34.9);
        data.put("totalOrders", 5260);
        data.put("campaignOrders", 1820);
        data.put("avgDiscount", 7.8);

        // 趋势数据
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days; i >= 0; i--) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", LocalDateTime.now().minusDays(i).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")));
            item.put("gmv", 8000 + (int) (Math.random() * 4000));
            item.put("orders", 80 + (int) (Math.random() * 60));
            trend.add(item);
        }
        data.put("trend", trend);
        return Result.success(data);
    }
}

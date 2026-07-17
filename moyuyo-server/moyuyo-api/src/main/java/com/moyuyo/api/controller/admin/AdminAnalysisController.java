package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Tag(name = "管理后台 - 数据分析")
@RestController
@RequestMapping("/api/admin/analysis")
@RequiredArgsConstructor
public class AdminAnalysisController {

    @Operation(summary = "漏斗分析数据")
    @GetMapping("/funnel")
    public Result<List<Map<String, Object>>> funnel() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"浏览商品", "12000", "100.0"},
                {"加入购物车", "6500", "54.2"},
                {"提交订单", "4200", "35.0"},
                {"支付成功", "3800", "31.7"},
                {"完成交易", "3600", "30.0"},
        };
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", row[0]);
            item.put("userCount", new Integer(row[1]));
            item.put("conversionRate", new BigDecimal(row[2]));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "RFM分析")
    @GetMapping("/rfm")
    public Result<List<Map<String, Object>>> rfm() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"Luna Chen", "3", "45", "12800.00", "高价值"},
                {"Milo Wang", "15", "28", "8600.00", "高价值"},
                {"Sophie Li", "45", "12", "3200.00", "一般发展"},
                {"Kevin Zhang", "90", "5", "1500.00", "一般挽留"},
                {"Emma Liu", "120", "3", "800.00", "流失"},
                {"Oliver Zhao", "2", "60", "25600.00", "高价值"},
                {"Amy Xu", "30", "18", "5600.00", "一般价值"},
                {"Jack Huang", "60", "8", "2100.00", "一般发展"},
        };
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerName", row[0]);
            item.put("rDays", new Integer(row[1]));
            item.put("fCount", new Integer(row[2]));
            item.put("mAmount", new BigDecimal(row[3]));
            item.put("rfmLevel", row[4]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "搜索分析KPI")
    @GetMapping("/search")
    public Result<Map<String, Object>> search() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSearches", 8520);
        result.put("searchUsers", 3200);
        result.put("noResultRate", new BigDecimal("8.5"));
        result.put("avgSearchesPerUser", new BigDecimal("2.66"));

        List<Map<String, Object>> hotWords = new ArrayList<>();
        String[][] words = {
                {"狗粮", "1250", "14.7"},
                {"猫砂", "980", "11.5"},
                {"宠物玩具", "760", "8.9"},
                {"牵引绳", "650", "7.6"},
                {"宠物窝", "540", "6.3"},
                {"猫抓板", "480", "5.6"},
                {"宠物零食", "420", "4.9"},
                {"宠物衣服", "380", "4.5"},
        };
        for (String[] w : words) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("keyword", w[0]);
            item.put("searchCount", new Integer(w[1]));
            item.put("percentage", new BigDecimal(w[2]));
            hotWords.add(item);
        }
        result.put("hotKeywords", hotWords);
        return Result.success(result);
    }

    @Operation(summary = "流量分析KPI")
    @GetMapping("/traffic")
    public Result<Map<String, Object>> traffic() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayVisitors", 12580);
        result.put("todayPageViews", 56800);
        result.put("bounceRate", new BigDecimal("42.5"));
        result.put("avgStayDuration", "3分28秒");

        List<Map<String, Object>> channels = new ArrayList<>();
        String[][] data = {
                {"直接访问", "18500", "32.6"},
                {"搜索引擎", "14200", "25.0"},
                {"社交媒体", "9800", "17.3"},
                {"外部链接", "7200", "12.7"},
                {"邮件营销", "4500", "7.9"},
                {"付费广告", "2600", "4.6"},
        };
        for (String[] c : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channelName", c[0]);
            item.put("visits", new Integer(c[1]));
            item.put("percentage", new BigDecimal(c[2]));
            channels.add(item);
        }
        result.put("channels", channels);
        return Result.success(result);
    }
}

package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - CRM管理")
@RestController
@RequestMapping("/api/admin/crm")
@RequiredArgsConstructor
public class AdminCrmController {

    @Operation(summary = "客服绩效列表")
    @GetMapping("/cs-performance")
    public Result<List<Map<String, Object>>> csPerformance(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"小王", "45", "1.5", "4.8", "7h32m"},
                {"小李", "38", "2.1", "4.6", "6h45m"},
                {"小张", "52", "1.8", "4.9", "8h10m"},
                {"小赵", "29", "3.2", "4.3", "5h50m"},
                {"小刘", "41", "2.5", "4.7", "7h00m"},
        };
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentId", new Random().nextInt(100));
            item.put("agentName", row[0]);
            item.put("ticketCount", new Integer(row[1]));
            item.put("avgResponseTime", new BigDecimal(row[2]));
            item.put("satisfactionScore", new BigDecimal(row[3]));
            item.put("todayOnlineDuration", row[4]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "客服详情")
    @GetMapping("/{agentId}/cs-detail")
    public Result<Map<String, Object>> csDetail(@PathVariable Long agentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentId", agentId);
        result.put("agentName", "小王");
        result.put("department", "客服一部");
        result.put("totalTickets", 1280);
        result.put("avgResponseTime", new BigDecimal("1.5"));
        result.put("satisfactionScore", new BigDecimal("4.8"));
        result.put("todayOnlineDuration", "7h32m");
        result.put("todayTickets", 45);
        result.put("status", "ONLINE");
        result.put("latestLogin", LocalDateTime.now().minusMinutes(30));
        return Result.success(result);
    }

    @Operation(summary = "实时大屏数据")
    @GetMapping("/realtime")
    public Result<Map<String, Object>> realtime() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("onlineUsers", 1268);
        result.put("todayVisitors", 12580);
        result.put("todayOrders", 568);
        result.put("todaySales", new BigDecimal("89650.00"));
        result.put("updateTime", LocalDateTime.now());
        return Result.success(result);
    }

    @Operation(summary = "实时订单流")
    @GetMapping("/realtime-order-flow")
    public Result<List<Map<String, Object>>> realtimeOrderFlow() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"MOY20260717001", "Luna Chen", "189.00", "PAID"},
                {"MOY20260717002", "Milo Wang", "56.00", "PAID"},
                {"MOY20260717003", "Sophie Li", "328.00", "PAID"},
                {"MOY20260717004", "Kevin Zhang", "45.00", "SUBMITTED"},
                {"MOY20260717005", "Emma Liu", "268.00", "PAID"},
                {"MOY20260717006", "Oliver Zhao", "99.00", "SUBMITTED"},
        };
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderNo", row[0]);
            item.put("userName", row[1]);
            item.put("amount", new BigDecimal(row[2]));
            item.put("status", row[3]);
            item.put("orderTime", LocalDateTime.now().minusMinutes((long) (Math.random() * 60)));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "热门商品排行榜")
    @GetMapping("/realtime/top-products")
    public Result<List<Map<String, Object>>> topProducts() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"宠物洗护套装", "128", "16512.00"},
                {"舒适胸背带-M", "96", "4704.00"},
                {"益智玩具球", "85", "2975.00"},
                {"宠物航空箱-L", "72", "21528.00"},
                {"鹿角磨牙棒", "68", "1496.00"},
                {"智能喂食器", "55", "16500.00"},
        };
        int rank = 1;
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            item.put("productName", row[0]);
            item.put("salesCount", new Integer(row[1]));
            item.put("salesAmount", new BigDecimal(row[2]));
            list.add(item);
        }
        return Result.success(list);
    }
}

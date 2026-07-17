package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 用户画像")
@RestController
@RequestMapping("/api/admin/user-profile")
@RequiredArgsConstructor
public class AdminUserProfileController {

    @Operation(summary = "用户画像信息")
    @GetMapping("/{userId}")
    public Result<Map<String, Object>> profile(@PathVariable Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("nickname", "Luna Chen");
        result.put("email", "luna@example.com");
        result.put("phone", "138****1234");
        result.put("gender", "女");
        result.put("age", 28);
        result.put("memberLevel", "VIP");
        result.put("registerTime", LocalDateTime.of(2024, 3, 15, 10, 30));
        result.put("totalConsumption", new BigDecimal("28600.00"));

        List<String> tags = Arrays.asList("高消费", "活跃用户", "宠物主", "忠诚客户");
        result.put("tags", tags);
        return Result.success(result);
    }

    @Operation(summary = "用户行为数据")
    @GetMapping("/{userId}/behaviors")
    public Result<List<Map<String, Object>>> behaviors(@PathVariable Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"浏览商品", "156", "2026-07-17 14:30:00"},
                {"搜索商品", "89", "2026-07-17 14:25:00"},
                {"加入购物车", "42", "2026-07-16 20:15:00"},
                {"提交订单", "18", "2026-07-16 20:10:00"},
                {"收藏商品", "35", "2026-07-15 18:00:00"},
                {"查看详情页", "210", "2026-07-17 14:28:00"},
        };
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("behaviorType", row[0]);
            item.put("count", new Integer(row[1]));
            item.put("lastTime", row[2]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "用户订单历史")
    @GetMapping("/{userId}/orders")
    public Result<List<Map<String, Object>>> orders(@PathVariable Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"MOY20260715001", "宠物洗护套装", "129.00", "COMPLETED"},
                {"MOY20260710002", "舒适胸背带-M", "49.00", "COMPLETED"},
                {"MOY20260705003", "益智玩具球", "35.00", "SHIPPED"},
                {"MOY20260628004", "宠物航空箱-L", "299.00", "COMPLETED"},
                {"MOY20260620005", "鹿角磨牙棒", "22.00", "CANCELLED"},
                {"MOY20260612006", "宠物粮食存储桶", "68.00", "COMPLETED"},
        };
        for (String[] row : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderNo", row[0]);
            item.put("productName", row[1]);
            item.put("amount", new BigDecimal(row[2]));
            item.put("status", row[3]);
            item.put("createTime", LocalDateTime.now().minusDays((long) (Math.random() * 30)));
            list.add(item);
        }
        return Result.success(list);
    }
}

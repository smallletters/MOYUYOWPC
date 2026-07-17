package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 直播管理")
@RestController
@RequestMapping("/api/admin/live")
@RequiredArgsConstructor
public class AdminLiveController {

    @Operation(summary = "直播间列表")
    @GetMapping("/rooms")
    public Result<List<Map<String, Object>>> rooms() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] titles = {"年中大促专场", "新品首发直播", "美食特惠日", "美妆护肤课堂", "数码好物推荐"};
        String[] anchors = {"主播小美", "主播阿杰", "主播小悦", "主播Lily", "主播阿强"};
        String[] statuses = {"LIVE", "LIVE", "SCHEDULED", "ENDED", "LIVE"};
        int[] viewers = {12500, 8800, 3200, 15600, 7200};
        int[] productCounts = {18, 12, 25, 30, 15};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("title", titles[i - 1]);
            item.put("anchor", anchors[i - 1]);
            item.put("status", statuses[i - 1]);
            item.put("viewerCount", viewers[i - 1]);
            item.put("productCount", productCounts[i - 1]);
            item.put("startTime", statuses[i - 1].equals("SCHEDULED")
                    ? LocalDateTime.now().plusDays(i)
                    : LocalDateTime.now().minusHours(i * 3));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "创建直播间")
    @PostMapping("/rooms")
    public Result<Map<String, Object>> createRoom(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("message", "直播间创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新直播间")
    @PutMapping("/rooms/{id}")
    public Result<Map<String, Object>> updateRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "直播间更新成功");
        return Result.success(result);
    }

    @Operation(summary = "更新直播状态")
    @PutMapping("/rooms/{id}/status")
    public Result<Map<String, Object>> updateRoomStatus(@PathVariable Long id, @RequestParam String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        result.put("message", "直播状态更新成功");
        return Result.success(result);
    }

    @Operation(summary = "直播间详情")
    @GetMapping("/rooms/{id}")
    public Result<Map<String, Object>> roomDetail(@PathVariable Long id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", "直播间" + id);
        item.put("anchor", "主播" + (id == 1 ? "小美" : id == 2 ? "阿杰" : "Lily"));
        item.put("status", "LIVE");
        item.put("viewerCount", 8000 + (int) (Math.random() * 5000));
        item.put("totalViewers", 35000 + (int) (Math.random() * 10000));
        item.put("productCount", 15 + (int) (Math.random() * 10));
        item.put("likeCount", 25000 + (int) (Math.random() * 15000));
        item.put("orderCount", 120 + (int) (Math.random() * 80));
        item.put("gmv", 35000 + (int) (Math.random() * 20000));
        item.put("startTime", LocalDateTime.now().minusHours(2));
        item.put("cover", "https://img.example.com/live_" + id + ".jpg");
        item.put("description", "这是直播间" + id + "的详细描述内容");

        // 商品列表
        List<Map<String, Object>> products = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("id", i);
            product.put("name", "直播商品" + i);
            product.put("price", 99.9 + i * 50);
            product.put("stock", 100 + i * 50);
            products.add(product);
        }
        item.put("products", products);
        return Result.success(item);
    }
}

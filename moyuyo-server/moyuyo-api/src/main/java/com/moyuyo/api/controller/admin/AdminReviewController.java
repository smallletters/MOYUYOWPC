package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 评价管理")
@RestController
@RequestMapping("/api/admin/review")
@RequiredArgsConstructor
public class AdminReviewController {

    @Operation(summary = "评价列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] products = {"天然猫粮5kg", "宠物窝垫四季通用", "智能饮水机Pro", "猫抓板仙人掌", "宠物湿巾80抽"};
        String[] users = {"张三", "李四", "王五", "赵六", "陈七"};
        int[] ratings = {5, 3, 4, 5, 2};
        String[] contents = {"非常棒！猫咪很喜欢吃", "一般般，包装有破损", "质量不错，性价比高", "用了两周，很满意", "不是很推荐，质量一般"};
        String[] statuses = {"已审核", "待审核", "已审核", "已审核", "待审核"};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("productName", products[i]);
            item.put("userName", users[i]);
            item.put("rating", ratings[i]);
            item.put("content", contents[i]);
            item.put("status", statuses[i]);
            item.put("createTime", LocalDateTime.now().minusDays(i + 1));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "审核通过")
    @PutMapping("/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "已审核");
        result.put("message", "审核通过成功");
        return Result.success(result);
    }

    @Operation(summary = "审核驳回")
    @PutMapping("/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "已驳回");
        result.put("message", "审核驳回成功");
        return Result.success(result);
    }

    @Operation(summary = "回复评价")
    @PostMapping("/{id}/reply")
    public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("reply", body.getOrDefault("content", ""));
        result.put("message", "回复成功");
        return Result.success(result);
    }
}

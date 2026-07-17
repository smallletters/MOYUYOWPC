package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 敏感词管理")
@RestController
@RequestMapping("/api/admin/sensitive")
@RequiredArgsConstructor
public class AdminSensitiveWordController {

    @Operation(summary = "敏感词列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] categories = {"SPAM", "VIOLENCE", "AD", "POLITICAL", "OTHER"};
        for (int i = 1; i <= 15; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("word", "敏感词" + i);
            item.put("category", categories[i % 5]);
            item.put("level", i % 3 == 0 ? "HIGH" : i % 2 == 0 ? "MEDIUM" : "LOW");
            item.put("status", i % 4 == 0 ? "DISABLED" : "ENABLED");
            item.put("hitCount", (int) (Math.random() * 100));
            item.put("createTime", LocalDateTime.now().minusDays(i));
            item.put("updateTime", LocalDateTime.now().minusHours(i));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "分类统计")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"SPAM", "广告垃圾", "156"},
                {"VIOLENCE", "暴力违禁", "89"},
                {"AD", "推广营销", "234"},
                {"POLITICAL", "政治敏感", "45"},
                {"OTHER", "其他", "67"},
        };
        for (String[] c : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", c[0]);
            item.put("name", c[1]);
            item.put("count", new Integer(c[2]));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "新增敏感词")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("word", body.get("word"));
        result.put("message", "敏感词新增成功");
        return Result.success(result);
    }

    @Operation(summary = "更新敏感词")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("word", body.get("word"));
        result.put("message", "敏感词更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除敏感词")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "敏感词删除成功");
        return Result.success(result);
    }

    @Operation(summary = "批量删除敏感词")
    @PostMapping("/batch-delete")
    public Result<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedCount", ids.size());
        result.put("message", "批量删除成功");
        return Result.success(result);
    }
}

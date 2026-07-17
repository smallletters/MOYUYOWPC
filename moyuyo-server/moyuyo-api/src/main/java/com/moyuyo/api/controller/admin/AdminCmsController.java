package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - CMS内容管理")
@RestController
@RequestMapping("/api/admin/cms")
@RequiredArgsConstructor
public class AdminCmsController {

    @Operation(summary = "CMS内容列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "BANNER") String type) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("title", type + "内容" + i);
            item.put("type", type);
            item.put("cover", "https://img.example.com/" + type.toLowerCase() + "_" + i + ".jpg");
            item.put("sortOrder", i);
            item.put("status", i % 3 == 0 ? "PAUSED" : "ACTIVE");
            item.put("createTime", LocalDateTime.now().minusDays(i));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "内容详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", "Banner内容" + id);
        item.put("type", "BANNER");
        item.put("cover", "https://img.example.com/banner_" + id + ".jpg");
        item.put("link", "https://moyuyo.com/promotion/" + id);
        item.put("sortOrder", id);
        item.put("status", "ACTIVE");
        item.put("startTime", LocalDateTime.now());
        item.put("endTime", LocalDateTime.now().plusDays(30));
        item.put("createTime", LocalDateTime.now().minusDays(7));
        item.put("description", "这是Banner内容" + id + "的详细描述");
        return Result.success(item);
    }

    @Operation(summary = "新建内容")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新内容")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除内容")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "更新内容状态")
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        result.put("message", "状态更新成功");
        return Result.success(result);
    }

    @Operation(summary = "拖拽排序")
    @PutMapping("/reorder")
    public Result<Map<String, Object>> reorder(@RequestBody List<Map<String, Object>> orders) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", orders.size());
        result.put("message", "排序更新成功");
        return Result.success(result);
    }
}

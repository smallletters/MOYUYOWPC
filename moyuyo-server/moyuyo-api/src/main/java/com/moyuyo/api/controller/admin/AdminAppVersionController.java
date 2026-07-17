package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - App版本管理")
@RestController
@RequestMapping("/api/admin/app-version")
@RequiredArgsConstructor
public class AdminAppVersionController {

    @Operation(summary = "版本列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] versions = {"3.2.0", "3.1.0", "3.0.1", "3.0.0", "2.9.0"};
        String[] platforms = {"iOS", "Android", "iOS", "Android", "iOS"};
        String[] contents = {
                "修复已知Bug，优化首页加载速度",
                "新增直播功能模块，优化购物车交互体验",
                "修复支付闪退问题，提升稳定性",
                "全新UI设计，新增个性化推荐",
                "优化搜索功能，增加商品对比功能"
        };
        String[] statuses = {"PUBLISHED", "PUBLISHED", "PUBLISHED", "PUBLISHED", "DRAFT"};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("version", versions[i - 1]);
            item.put("platform", platforms[i - 1]);
            item.put("content", contents[i - 1]);
            item.put("status", statuses[i - 1]);
            item.put("publishTime", !statuses[i - 1].equals("DRAFT") ? LocalDateTime.now().minusDays(i * 7) : null);
            item.put("createTime", LocalDateTime.now().minusDays(i * 7 + 3));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "创建版本")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("version", body.getOrDefault("version", "1.0.0"));
        result.put("message", "版本创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新版本")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "版本更新成功");
        return Result.success(result);
    }

    @Operation(summary = "发布版本")
    @PostMapping("/{id}/publish")
    public Result<Map<String, Object>> publish(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("publishTime", LocalDateTime.now());
        result.put("message", "版本发布成功");
        return Result.success(result);
    }
}

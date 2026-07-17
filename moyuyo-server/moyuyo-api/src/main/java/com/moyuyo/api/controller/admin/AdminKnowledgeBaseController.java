package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 知识库管理")
@RestController
@RequestMapping("/api/admin/knowledge-base")
@RequiredArgsConstructor
public class AdminKnowledgeBaseController {

    @Operation(summary = "知识库列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] titles = {
                "如何发布商品到海外站",
                "国际物流运费计算规则",
                "跨境电商海关申报指南",
                "平台入驻常见问题FAQ",
                "售后处理流程与规范"
        };
        String[] categories = {"FAQ", "GUIDE", "POLICY", "FAQ", "GUIDE"};
        String[] statuses = {"PUBLISHED", "PUBLISHED", "PUBLISHED", "DRAFT", "PUBLISHED"};
        int[] viewCounts = {12580, 8600, 5200, 1800, 7200};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("title", titles[i - 1]);
            item.put("category", categories[i - 1]);
            item.put("status", statuses[i - 1]);
            item.put("viewCount", viewCounts[i - 1]);
            item.put("updateTime", LocalDateTime.now().minusDays(i));
            item.put("createTime", LocalDateTime.now().minusDays(i + 10));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "创建文章")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("message", "文章创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新文章")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "文章更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "分类列表")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[][] data = {
                {"FAQ", "常见问题", "12"},
                {"GUIDE", "操作指南", "8"},
                {"POLICY", "政策法规", "5"},
        };
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("code", data[i - 1][0]);
            item.put("name", data[i - 1][1]);
            item.put("articleCount", Integer.parseInt(data[i - 1][2]));
            list.add(item);
        }
        return Result.success(list);
    }
}

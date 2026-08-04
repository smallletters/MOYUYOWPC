package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import com.moyuyo.service.admin.SensitiveWordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 敏感词管理")
@RestController
@RequestMapping("/api/admin/sensitive")
@RequiredArgsConstructor
public class AdminSensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    @Operation(summary = "敏感词列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        List<SensitiveWordEntity> entities = sensitiveWordService.listAll(category, keyword);
        List<Map<String, Object>> list = new ArrayList<>();
        for (SensitiveWordEntity e : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("word", e.getWord());
            item.put("category", e.getCategory());
            item.put("status", e.getStatus());
            item.put("hitCount", e.getHitCount());
            item.put("createTime", e.getCreateTime());
            item.put("updateTime", e.getUpdateTime());
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "分类统计")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        // 从数据库按 category 分组统计真实数据
        List<Map<String, Object>> list = sensitiveWordService.categoryStats();
        return Result.success(list);
    }

    @Operation(summary = "新增敏感词")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SensitiveWordEntity entity = new SensitiveWordEntity();
        entity.setWord((String) body.get("word"));
        entity.setCategory((String) body.get("category"));
        entity.setStatus((String) body.get("status"));
        sensitiveWordService.create(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("word", entity.getWord());
        result.put("message", "敏感词新增成功");
        return Result.success(result);
    }

    @Operation(summary = "更新敏感词")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        // 校验 id 必填
        if (body == null || body.get("id") == null) {
            return Result.error(400, "参数错误：id 不能为空");
        }
        Long id;
        try {
            id = Long.valueOf(body.get("id").toString());
        } catch (NumberFormatException e) {
            return Result.error(400, "参数错误：id 必须为数字");
        }
        // 校验 word 必填
        Object wordObj = body.get("word");
        if (wordObj == null || wordObj.toString().isEmpty()) {
            return Result.error(400, "参数错误：word 不能为空");
        }
        SensitiveWordEntity entity = new SensitiveWordEntity();
        entity.setId(id);
        entity.setWord(wordObj.toString());
        entity.setCategory((String) body.get("category"));
        entity.setStatus((String) body.get("status"));
        sensitiveWordService.update(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("word", entity.getWord());
        result.put("message", "敏感词更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除敏感词")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "敏感词删除成功");
        return Result.success(result);
    }

    @Operation(summary = "批量删除敏感词")
    @PostMapping("/batch-delete")
    public Result<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        sensitiveWordService.batchDelete(ids);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedCount", ids.size());
        result.put("message", "批量删除成功");
        return Result.success(result);
    }
}

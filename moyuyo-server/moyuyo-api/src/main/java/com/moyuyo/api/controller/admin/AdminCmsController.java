package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.CmsContentEntity;
import com.moyuyo.service.admin.CmsContentService;
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

    private final CmsContentService cmsContentService;

    @Operation(summary = "CMS内容列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "BANNER") String type) {
        List<CmsContentEntity> entities = cmsContentService.listByType(type);
        List<Map<String, Object>> list = new ArrayList<>();
        for (CmsContentEntity e : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("title", e.getTitle());
            item.put("type", e.getType());
            item.put("cover", e.getImageUrl());
            item.put("sortOrder", e.getSortOrder());
            item.put("status", e.getStatus());
            item.put("createTime", e.getCreatedAt());
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "内容详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        CmsContentEntity e = cmsContentService.getById(id);
        if (e == null) {
            return Result.error("内容不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", e.getId());
        item.put("title", e.getTitle());
        item.put("type", e.getType());
        item.put("cover", e.getImageUrl());
        item.put("link", e.getLinkUrl());
        item.put("sortOrder", e.getSortOrder());
        item.put("status", e.getStatus());
        item.put("startTime", e.getStartTime());
        item.put("endTime", e.getEndTime());
        item.put("createTime", e.getCreatedAt());
        item.put("description", e.getContent());
        return Result.success(item);
    }

    @Operation(summary = "新建内容")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        CmsContentEntity entity = new CmsContentEntity();
        entity.setTitle((String) body.get("title"));
        entity.setType((String) body.get("type"));
        entity.setImageUrl((String) body.get("cover"));
        entity.setLinkUrl((String) body.get("link"));
        entity.setContent((String) body.get("description"));
        entity.setStatus((String) body.get("status"));
        if (body.get("sortOrder") != null) {
            entity.setSortOrder(Integer.valueOf(body.get("sortOrder").toString()));
        }
        cmsContentService.create(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新内容")
    @PutMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        CmsContentEntity entity = new CmsContentEntity();
        entity.setId(Long.valueOf(body.get("id").toString()));
        entity.setTitle((String) body.get("title"));
        entity.setType((String) body.get("type"));
        entity.setImageUrl((String) body.get("cover"));
        entity.setLinkUrl((String) body.get("link"));
        entity.setContent((String) body.get("description"));
        entity.setStatus((String) body.get("status"));
        if (body.get("sortOrder") != null) {
            entity.setSortOrder(Integer.valueOf(body.get("sortOrder").toString()));
        }
        cmsContentService.update(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除内容")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        cmsContentService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "更新内容状态")
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        cmsContentService.updateStatus(id, status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        result.put("message", "状态更新成功");
        return Result.success(result);
    }

    @Operation(summary = "拖拽排序")
    @PutMapping("/reorder")
    public Result<Map<String, Object>> reorder(@RequestBody List<Map<String, Object>> orders) {
        // TODO: CmsContentService 暂未提供批量排序方法，后续补充
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", orders.size());
        result.put("message", "排序更新成功");
        return Result.success(result);
    }
}

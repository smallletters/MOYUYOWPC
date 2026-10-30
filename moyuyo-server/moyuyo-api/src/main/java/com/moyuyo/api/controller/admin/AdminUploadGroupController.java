package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminUploadGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端图片空间分组 Controller。
 * <p>
 * 路由说明：
 * - GET/POST/PUT/DELETE /api/admin/upload/groups...：分组增删改查
 * - PUT /api/admin/upload/group：批量调整图片归属
 */
@Slf4j
@Tag(name = "管理后台 - 图片空间分组")
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class AdminUploadGroupController {

    private final AdminUploadGroupService adminUploadGroupService;

    @GetMapping("/groups")
    @Operation(summary = "查询图片分组列表")
    public Result<List<Map<String, Object>>> listGroups() {
        return Result.success(adminUploadGroupService.listGroups());
    }

    @PostMapping("/groups")
    @Operation(summary = "创建图片分组")
    public Result<Map<String, Object>> createGroup(@RequestBody Map<String, String> body) {
        try {
            Long id = adminUploadGroupService.createGroup(body == null ? null : body.get("name"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("message", "分组创建成功");
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[upload] 创建分组失败", e);
            return Result.error("创建分组失败");
        }
    }

    @PutMapping("/groups/{id}")
    @Operation(summary = "重命名图片分组")
    public Result<Map<String, Object>> renameGroup(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            adminUploadGroupService.renameGroup(id, body == null ? null : body.get("name"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("message", "分组重命名成功");
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[upload] 重命名分组失败：id={}", id, e);
            return Result.error("重命名分组失败");
        }
    }

    @DeleteMapping("/groups/{id}")
    @Operation(summary = "删除图片分组（图片回到未分组）")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        try {
            adminUploadGroupService.deleteGroup(id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[upload] 删除分组失败：id={}", id, e);
            return Result.error("删除分组失败");
        }
    }

    /**
     * 批量调整图片分组。
     * <p>
     * PUT /api/admin/upload/group
     * body: { "urls": ["/uploads/..."], "groupId": 12 }，groupId=0 表示移出分组。
     */
    @PutMapping("/group")
    @Operation(summary = "批量调整图片分组")
    public Result<Map<String, Object>> assignGroup(@RequestBody Map<String, Object> body) {
        try {
            List<String> urls = parseUrls(body == null ? null : body.get("urls"));
            Long groupId = parseLong(body == null ? null : body.get("groupId"));
            adminUploadGroupService.assignGroup(urls, groupId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "分组调整成功");
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("[upload] 调整图片分组失败", e);
            return Result.error("调整图片分组失败");
        }
    }

    /** 解析 urls 数组，非数组或元素非字符串时忽略 */
    private List<String> parseUrls(Object value) {
        List<String> urls = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    urls.add(String.valueOf(item));
                }
            }
        }
        return urls;
    }

    /** 兼容数字 / 字符串两种 groupId 传参 */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.PushRecordEntity;
import com.moyuyo.service.admin.PushManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 推送管理")
@RestController
@RequestMapping("/api/admin/push")
@RequiredArgsConstructor
public class AdminPushController {

    private final PushManageService pushManageService;

    @Operation(summary = "推送统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(pushManageService.getStats());
    }

    @Operation(summary = "推送记录列表")
    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<PushRecordEntity> entities = pushManageService.listRecords(null, null);
        List<Map<String, Object>> list = new ArrayList<>();
        for (PushRecordEntity e : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("title", e.getTitle());
            item.put("content", e.getContent());
            item.put("channel", e.getType());
            item.put("status", e.getStatus());
            item.put("sentCount", e.getSuccessCount() != null ? e.getSuccessCount() : 0);
            item.put("openCount", 0);
            item.put("clickCount", 0);
            item.put("sendTime", e.getSentTime());
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "新建推送")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        PushRecordEntity entity = new PushRecordEntity();
        entity.setTitle((String) body.get("title"));
        entity.setContent((String) body.get("content"));
        entity.setType((String) body.get("channel"));
        pushManageService.create(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("message", "推送创建成功");
        return Result.success(result);
    }

    @Operation(summary = "发送推送")
    @PostMapping("/{id}/send")
    public Result<Map<String, Object>> send(@PathVariable Long id) {
        pushManageService.send(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送发送成功");
        return Result.success(result);
    }

    @Operation(summary = "取消推送")
    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id) {
        pushManageService.cancel(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送已取消");
        return Result.success(result);
    }

    @Operation(summary = "删除推送")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        pushManageService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送已删除");
        return Result.success(result);
    }

    @Operation(summary = "定时推送列表")
    @GetMapping("/scheduled")
    public Result<List<Map<String, Object>>> scheduled() {
        List<PushRecordEntity> entities = pushManageService.listScheduledRecords();
        List<Map<String, Object>> list = new ArrayList<>();
        for (PushRecordEntity e : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("title", e.getTitle());
            item.put("scheduledTime", e.getScheduledTime());
            item.put("type", e.getType());
            item.put("status", e.getStatus());
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "设置定时推送")
    @PostMapping("/schedule")
    public Result<Map<String, Object>> schedule(@RequestBody Map<String, Object> body) {
        PushRecordEntity entity = new PushRecordEntity();
        entity.setTitle((String) body.get("title"));
        entity.setContent((String) body.get("content"));
        entity.setType((String) body.get("type"));
        if (body.get("scheduledTime") != null) {
            entity.setScheduledTime(LocalDateTime.parse((String) body.get("scheduledTime")));
        }
        pushManageService.saveSchedule(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("scheduledTime", entity.getScheduledTime());
        result.put("message", "定时推送设置成功");
        return Result.success(result);
    }
}

package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.dao.admin.entity.PushRecordEntity;
import com.moyuyo.dao.admin.mapper.PushRecordMapper;
import com.moyuyo.service.admin.PushManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Tag(name = "管理后台 - 推送管理")
@RestController
@RequestMapping("/api/admin/push")
@RequiredArgsConstructor
public class AdminPushController {

    private final PushManageService pushManageService;
    private final PushRecordMapper pushRecordMapper;

    @Operation(summary = "推送统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(pushManageService.getStats());
    }

    @Operation(summary = "推送记录列表")
    @GetMapping("/records")
    public Result<Map<String, Object>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        // 使用 MyBatis-Plus Page 进行数据库分页查询
        // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（Function<T,R> 类型推断）
        @SuppressWarnings("null")
        Page<PushRecordEntity> pageResult = pushRecordMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<PushRecordEntity>()
                .orderByDesc(PushRecordEntity::getCreateTime));

        List<Map<String, Object>> list = new ArrayList<>();
        for (PushRecordEntity e : pageResult.getRecords()) {
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", list);
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        return Result.success(result);
    }

    @Operation(summary = "新建推送")
    @PostMapping("/create")
    @AdminAudit(action = "CREATE", module = "MARKETING",
        resourceId = "push", detail = "管理员创建推送任务")
    public Result<?> create(@RequestBody Map<String, Object> body) {
        // 前置参数校验：title/content 必填，避免数据库 NOT NULL 异常被全局异常捕获为 409
        String title = (String) body.get("title");
        if (title == null || title.trim().isEmpty()) {
            return Result.error(400, "推送标题不能为空");
        }
        String content = (String) body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.error(400, "推送内容不能为空");
        }
        PushRecordEntity entity = new PushRecordEntity();
        entity.setTitle(title);
        entity.setContent(content);
        // 前端可能传 channel 字段，映射到 type 字段；缺失时默认 NOTICE 避免 NOT NULL 异常
        String channel = (String) body.get("channel");
        if (channel == null || channel.trim().isEmpty()) {
            channel = (String) body.get("type");
        }
        if (channel == null || channel.trim().isEmpty()) {
            channel = "NOTICE";
        }
        entity.setType(channel);
        pushManageService.create(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("message", "推送创建成功");
        return Result.success(result);
    }

    @Operation(summary = "发送推送")
    @PostMapping("/{id}/send")
    @AdminAudit(action = "UPDATE", module = "MARKETING",
        resourceId = "#id", detail = "管理员发送推送")
    public Result<Map<String, Object>> send(@PathVariable Long id) {
        pushManageService.send(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送发送成功");
        return Result.success(result);
    }

    @Operation(summary = "推送详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        PushRecordEntity entity = pushRecordMapper.selectById(id);
        if (entity == null) {
            return Result.error("推送记录不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId());
        item.put("title", entity.getTitle());
        item.put("content", entity.getContent());
        item.put("type", entity.getType());
        item.put("targetType", entity.getTargetType());
        item.put("targetIds", entity.getTargetIds());
        item.put("status", entity.getStatus());
        item.put("scheduledTime", entity.getScheduledTime());
        item.put("sentTime", entity.getSentTime());
        item.put("successCount", entity.getSuccessCount());
        item.put("failCount", entity.getFailCount());
        item.put("createTime", entity.getCreateTime());
        item.put("updateTime", entity.getUpdateTime());
        return Result.success(item);
    }

    @Operation(summary = "更新推送")
    @PutMapping("/{id}")
    @AdminAudit(action = "UPDATE", module = "MARKETING",
        resourceId = "#id", detail = "管理员更新推送")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PushRecordEntity entity = pushRecordMapper.selectById(id);
        if (entity == null) {
            return Result.error("推送记录不存在");
        }
        if (body.containsKey("title")) entity.setTitle((String) body.get("title"));
        if (body.containsKey("content")) entity.setContent((String) body.get("content"));
        if (body.containsKey("type")) entity.setType((String) body.get("type"));
        if (body.containsKey("channel")) entity.setType((String) body.get("channel"));
        if (body.containsKey("targetType")) entity.setTargetType((String) body.get("targetType"));
        if (body.containsKey("targetIds")) entity.setTargetIds((String) body.get("targetIds"));
        pushManageService.update(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送更新成功");
        return Result.success(result);
    }

    @Operation(summary = "取消推送")
    @PostMapping("/{id}/cancel")
    @AdminAudit(action = "UPDATE", module = "MARKETING",
        resourceId = "#id", detail = "管理员取消推送")
    public Result<?> cancel(@PathVariable Long id) {
        pushManageService.cancel(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送已取消");
        return Result.success(result);
    }

    @Operation(summary = "取消定时推送")
    @PostMapping("/scheduled/{id}/cancel")
    @AdminAudit(action = "UPDATE", module = "MARKETING",
        resourceId = "#id", detail = "管理员取消定时推送")
    public Result<?> cancelScheduled(@PathVariable Long id) {
        pushManageService.cancel(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "定时推送已取消");
        return Result.success(result);
    }

    @Operation(summary = "删除推送")
    @DeleteMapping("/{id}")
    @AdminAudit(action = "DELETE", module = "MARKETING",
        resourceId = "#id", detail = "管理员删除推送")
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
    @AdminAudit(action = "CREATE", module = "MARKETING",
        resourceId = "push-schedule", detail = "管理员设置定时推送")
    public Result<?> schedule(@RequestBody Map<String, Object> body) {
        PushRecordEntity entity = new PushRecordEntity();
        entity.setTitle((String) body.get("title"));
        entity.setContent((String) body.get("content"));
        // 前端可能传 channel 字段，映射到 type 字段
        String type = (String) body.get("type");
        if (type == null && body.containsKey("channel")) {
          type = (String) body.get("channel");
        }
        entity.setType(type);
        if (body.get("scheduledTime") != null) {
            String timeStr = (String) body.get("scheduledTime");
            try {
                entity.setScheduledTime(LocalDateTime.parse(timeStr));
            } catch (DateTimeParseException e) {
                // 兼容 yyyy-MM-dd HH:mm:ss 格式
                try {
                    entity.setScheduledTime(LocalDateTime.parse(timeStr,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (DateTimeParseException ex) {
                    return Result.error(400, "定时时间格式无效: " + timeStr);
                }
            }
        }
        pushManageService.saveSchedule(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("scheduledTime", entity.getScheduledTime());
        result.put("message", "定时推送设置成功");
        return Result.success(result);
    }
}

package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
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

    @Operation(summary = "推送统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalSent", 15800);
        data.put("totalOpens", 4820);
        data.put("openRate", 30.5);
        data.put("totalClicks", 1560);
        data.put("clickRate", 9.9);

        // 今日数据
        data.put("todaySent", 320);
        data.put("todayOpens", 98);
        data.put("todayClicks", 32);

        // 渠道分布
        List<Map<String, Object>> channelStats = new ArrayList<>();
        String[][] channels = {
                {"NOTIFICATION", "系统通知", "6000", "2200", "800"},
                {"SMS", "短信", "5000", "1500", "400"},
                {"EMAIL", "邮件", "3000", "720", "200"},
                {"WECHAT", "微信模板", "1800", "400", "160"},
        };
        for (String[] ch : channels) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channel", ch[0]);
            item.put("name", ch[1]);
            item.put("sent", new Integer(ch[2]));
            item.put("opens", new Integer(ch[3]));
            item.put("clicks", new Integer(ch[4]));
            channelStats.add(item);
        }
        data.put("channelStats", channelStats);
        return Result.success(data);
    }

    @Operation(summary = "推送记录列表")
    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("title", "推送标题" + i);
            item.put("content", "这是推送内容" + i);
            item.put("channel", i % 4 == 0 ? "SMS" : i % 3 == 0 ? "EMAIL" : "NOTIFICATION");
            item.put("target", i % 2 == 0 ? "ALL" : "PARTIAL");
            item.put("status", i % 5 == 0 ? "FAILED" : i % 3 == 0 ? "SENDING" : "SENT");
            item.put("sentCount", (int) (Math.random() * 5000));
            item.put("openCount", (int) (Math.random() * 1500));
            item.put("clickCount", (int) (Math.random() * 500));
            item.put("sendTime", LocalDateTime.now().minusHours(i * 5));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "新建推送")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000));
        result.put("message", "推送创建成功");
        return Result.success(result);
    }

    @Operation(summary = "发送推送")
    @PostMapping("/{id}/send")
    public Result<Map<String, Object>> send(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送发送成功");
        return Result.success(result);
    }

    @Operation(summary = "取消推送")
    @PostMapping("/{id}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送已取消");
        return Result.success(result);
    }

    @Operation(summary = "删除推送")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "推送已删除");
        return Result.success(result);
    }

    @Operation(summary = "定时推送列表")
    @GetMapping("/scheduled")
    public Result<List<Map<String, Object>>> scheduled() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("title", "定时推送" + i);
            item.put("scheduledTime", LocalDateTime.now().plusDays(i));
            item.put("status", "SCHEDULED");
            item.put("channel", "NOTIFICATION");
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "设置定时推送")
    @PostMapping("/{id}/schedule")
    public Result<Map<String, Object>> schedule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("scheduledTime", body.get("scheduledTime"));
        result.put("message", "定时推送设置成功");
        return Result.success(result);
    }
}

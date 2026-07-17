package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 客服工单管理")
@RestController
@RequestMapping("/api/admin/ticket")
@RequiredArgsConstructor
public class AdminTicketController {

    private final AdminTicketService ticketService;

    @Operation(summary = "工单列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword) {
        return Result.success(ticketService.listAll(status, type, priority, keyword));
    }

    @Operation(summary = "工单统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingCount", 12);
        data.put("processingCount", 8);
        data.put("closedToday", 15);
        data.put("totalToday", 35);
        data.put("slaRate", 92.5);

        // 按类型统计
        List<Map<String, Object>> typeStats = new ArrayList<>();
        String[][] types = {
                {"REFUND", "退款售后", "18"},
                {"LOGISTICS", "物流查询", "12"},
                {"CONSULTATION", "商品咨询", "15"},
                {"COMPLAINT", "投诉建议", "5"},
        };
        for (String[] t : types) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", t[0]);
            item.put("name", t[1]);
            item.put("count", new Integer(t[2]));
            typeStats.add(item);
        }
        data.put("typeStats", typeStats);
        return Result.success(data);
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("ticketNo", "TK-202607" + String.format("%04d", id));
        data.put("title", "商品质量问题");
        data.put("type", "REFUND");
        data.put("priority", "HIGH");
        data.put("status", "PROCESSING");
        data.put("userName", "用户A");
        data.put("userContact", "user@example.com");
        data.put("assignee", "客服小王");
        data.put("description", "收到的商品有质量问题，存在明显划痕，要求退货退款。");
        data.put("createTime", LocalDateTime.now().minusDays(1));

        // 回复列表
        List<Map<String, Object>> replies = new ArrayList<>();
        Map<String, Object> reply1 = new LinkedHashMap<>();
        reply1.put("content", "已收到您的反馈，我们会尽快处理。");
        reply1.put("operator", "客服小王");
        reply1.put("createTime", LocalDateTime.now().minusHours(20));
        replies.add(reply1);
        Map<String, Object> reply2 = new LinkedHashMap<>();
        reply2.put("content", "这边已为您申请退货退款，请将商品寄回。");
        reply2.put("operator", "客服小王");
        reply2.put("createTime", LocalDateTime.now().minusHours(10));
        replies.add(reply2);
        data.put("replies", replies);
        return Result.success(data);
    }

    @Operation(summary = "分配客服")
    @PutMapping("/{id}/assign")
    public Result<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("assignee", body.get("assignee"));
        result.put("message", "分配成功");
        return Result.success(result);
    }

    @Operation(summary = "更新工单状态")
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", body.get("status"));
        result.put("message", "状态更新成功");
        return Result.success(result);
    }

    @Operation(summary = "回复工单")
    @PostMapping("/{id}/reply")
    public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("content", body.get("content"));
        result.put("message", "回复成功");
        return Result.success(result);
    }
}

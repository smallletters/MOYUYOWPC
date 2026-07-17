package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 投诉管理")
@RestController
@RequestMapping("/api/admin/complaint")
@RequiredArgsConstructor
public class AdminComplaintController {

    @Operation(summary = "投诉列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] types = {"商品质量", "物流延误", "服务态度", "退换货纠纷", "虚假宣传"};
        String[] statuses = {"PENDING", "PROCESSING", "CLOSED", "PROCESSING", "PENDING"};
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("complaintNo", "CP" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%04d", i));
            item.put("complainant", "用户" + (1000 + i));
            item.put("respondent", "商家" + (2000 + i));
            item.put("type", types[i - 1]);
            item.put("status", statuses[i - 1]);
            item.put("submitTime", LocalDateTime.now().minusDays(i * 2));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "投诉详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("complaintNo", "CP" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%04d", id));
        data.put("complainant", "用户" + (1000 + id));
        data.put("respondent", "商家" + (2000 + id));
        data.put("type", "商品质量");
        data.put("status", "PROCESSING");
        data.put("description", "收到的商品与描述不符，存在明显色差，且面料与页面标注不一致，要求退货退款并赔偿。");
        data.put("orderNo", "MOY202607" + String.format("%04d", id));
        data.put("submitTime", LocalDateTime.now().minusDays(3));
        data.put("assignee", "张三");
        data.put("processTime", LocalDateTime.now().minusDays(2));

        // 处理记录
        List<Map<String, Object>> processRecords = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", i);
            record.put("action", i == 1 ? "分配处理人" : "联系用户核实");
            record.put("operator", "张三");
            record.put("remark", i == 1 ? "已分配给张三处理" : "已与用户电话沟通，确认情况属实");
            record.put("createTime", LocalDateTime.now().minusDays(3 - i));
            processRecords.add(record);
        }
        data.put("processRecords", processRecords);

        return Result.success(data);
    }

    @Operation(summary = "开始处理")
    @PostMapping("/{id}/start-process")
    public Result<Map<String, Object>> startProcess(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "PROCESSING");
        result.put("operator", "当前用户");
        result.put("processTime", LocalDateTime.now());
        result.put("message", "已开始处理该投诉");
        return Result.success(result);
    }

    @Operation(summary = "完结投诉")
    @PostMapping("/{id}/close")
    public Result<Map<String, Object>> close(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "CLOSED");
        result.put("remark", body.get("remark"));
        result.put("operator", "当前用户");
        result.put("closeTime", LocalDateTime.now());
        result.put("message", "投诉已完结");
        return Result.success(result);
    }

    @Operation(summary = "分配处理人")
    @PutMapping("/{id}/assign")
    public Result<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("assignee", body.get("assignee"));
        result.put("operator", "当前用户");
        result.put("assignTime", LocalDateTime.now());
        result.put("message", "处理人已分配");
        return Result.success(result);
    }
}

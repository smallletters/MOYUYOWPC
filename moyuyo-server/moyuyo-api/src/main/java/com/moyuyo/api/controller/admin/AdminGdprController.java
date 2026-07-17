package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - GDPR合规")
@RestController
@RequestMapping("/api/admin/gdpr")
@RequiredArgsConstructor
public class AdminGdprController {

    @Operation(summary = "GDPR合规概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("privacyPolicyVersion", "V2.3.1");
        result.put("consentUserCount", 15682);
        result.put("pendingRequestCount", 7);
        result.put("lastUpdated", LocalDateTime.now().minusDays(3));

        // 数据主体请求列表
        List<Map<String, Object>> requests = new ArrayList<>();
        String[] users = {"user_001@test.com", "user_002@test.com", "user_003@test.com", "user_004@test.com", "user_005@test.com"};
        String[] types = {"数据导出", "数据删除", "更正信息", "限制处理", "数据导出"};
        String[] statuses = {"待处理", "待处理", "已完成", "处理中", "待处理"};
        for (int i = 0; i < 5; i++) {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("id", i + 1);
            req.put("userName", users[i]);
            req.put("type", types[i]);
            req.put("status", statuses[i]);
            req.put("submitTime", LocalDateTime.now().minusDays(i * 3 + 1));
            requests.add(req);
        }
        result.put("dataRequests", requests);
        return Result.success(result);
    }

    @Operation(summary = "用户同意记录列表")
    @GetMapping("/consent-records")
    public Result<List<Map<String, Object>>> consentRecords() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] users = {"user_a@test.com", "user_b@test.com", "user_c@test.com", "user_d@test.com", "user_e@test.com"};
        String[] types = {"隐私政策", "营销推送", "数据分析", "隐私政策", "第三方共享"};
        String[] statuses = {"已同意", "已同意", "已拒绝", "已同意", "已同意"};
        String[] ips = {"192.168.1.10", "192.168.1.25", "192.168.1.37", "192.168.1.42", "192.168.1.58"};
        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("userName", users[i]);
            item.put("consentType", types[i]);
            item.put("status", statuses[i]);
            item.put("ipAddress", ips[i]);
            item.put("createTime", LocalDateTime.now().minusDays(i * 2 + 1));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "数据主体请求列表")
    @GetMapping("/data-requests")
    public Result<List<Map<String, Object>>> dataRequests() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] requestNos = {"DSR-2026-001", "DSR-2026-002", "DSR-2026-003", "DSR-2026-004", "DSR-2026-005"};
        String[] users = {"user_x@test.com", "user_y@test.com", "user_z@test.com", "user_w@test.com", "user_v@test.com"};
        String[] types = {"数据导出", "数据删除", "限制处理", "更正信息", "数据可携"};
        String[] statuses = {"待处理", "处理中", "已完成", "待处理", "已完成"};
        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("requestNo", requestNos[i]);
            item.put("userName", users[i]);
            item.put("type", types[i]);
            item.put("status", statuses[i]);
            item.put("submitTime", LocalDateTime.now().minusDays(i * 4 + 1));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "处理数据请求")
    @PutMapping("/{id}/process")
    public Result<Map<String, Object>> processRequest(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", "处理中");
        result.put("message", "数据请求已受理");
        return Result.success(result);
    }

    @Operation(summary = "导出用户数据")
    @GetMapping("/export/{userId}")
    public Result<Map<String, Object>> exportUserData(@PathVariable String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("userName", "用户_" + userId);
        data.put("email", userId + "@test.com");
        data.put("registeredAt", LocalDateTime.now().minusMonths(6));
        data.put("lastLogin", LocalDateTime.now().minusDays(2));

        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderNo", "ORD2026" + String.format("%08d", i));
            order.put("amount", 99.9 + i * 50);
            order.put("status", "已完成");
            order.put("createTime", LocalDateTime.now().minusMonths(i));
            orders.add(order);
        }
        data.put("orders", orders);

        List<Map<String, Object>> consents = new ArrayList<>();
        String[] types = {"隐私政策", "营销推送", "数据分析"};
        for (int i = 0; i < 3; i++) {
            Map<String, Object> consent = new LinkedHashMap<>();
            consent.put("consentType", types[i]);
            consent.put("status", "已同意");
            consent.put("createTime", LocalDateTime.now().minusMonths(5));
            consents.add(consent);
        }
        data.put("consentRecords", consents);

        return Result.success(data);
    }
}

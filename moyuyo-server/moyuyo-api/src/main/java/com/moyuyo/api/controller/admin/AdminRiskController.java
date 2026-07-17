package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理后台 - 风控管理")
@RestController
@RequestMapping("/api/admin/risk")
@RequiredArgsConstructor
public class AdminRiskController {

    @Operation(summary = "风控规则列表")
    @GetMapping("/rules")
    public Result<List<Map<String, Object>>> rules() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] names = {"短时间内高频下单", "异常退款触发", "批量注册检测", "异地登录告警", "恶意刷券检测"};
        String[] levels = {"高", "中", "高", "中", "低"};
        int[] hitCounts = {156, 43, 892, 67, 12};
        String[] conditions = {"1分钟内下单>5次", "7天内退款率>50%", "同一IP注册>10账号/小时", "登录IP与常用地不符", "同一账号领券>100张/天"};
        String[] statuses = {"启用", "启用", "启用", "禁用", "启用"};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("ruleName", names[i]);
            item.put("riskLevel", levels[i]);
            item.put("hitCount", hitCounts[i]);
            item.put("condition", conditions[i]);
            item.put("status", statuses[i]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "创建规则")
    @PostMapping("/rules")
    public Result<Map<String, Object>> createRule(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", new Random().nextInt(1000) + 100);
        result.put("ruleName", body.getOrDefault("ruleName", ""));
        result.put("riskLevel", body.getOrDefault("riskLevel", "低"));
        result.put("status", "启用");
        result.put("message", "规则创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新规则")
    @PutMapping("/rules/{id}")
    public Result<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("ruleName", body.getOrDefault("ruleName", ""));
        result.put("riskLevel", body.getOrDefault("riskLevel", ""));
        result.put("message", "规则更新成功");
        return Result.success(result);
    }

    @Operation(summary = "启用/禁用规则")
    @PutMapping("/rules/{id}/status")
    public Result<Map<String, Object>> toggleRuleStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", body.getOrDefault("status", "启用"));
        result.put("message", "规则状态更新成功");
        return Result.success(result);
    }

    @Operation(summary = "规则引擎配置")
    @GetMapping("/engine")
    public Result<List<Map<String, Object>>> engine() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] ruleNames = {"高频下单限流", "异常退款审核", "批量注册拦截", "异地登录验证", "恶意刷券限制"};
        int[] priorities = {1, 2, 3, 4, 5};
        String[] actions = {"限流+告警", "转人工审核", "直接拦截", "二次验证", "限领+告警"};
        String[] statuses = {"启用", "启用", "启用", "禁用", "启用"};
        String[] conditions = {"{窗口:1分钟,阈值:5次}", "{窗口:7天,阈值:50%}", "{窗口:1小时,阈值:10}", "{窗口:实时}", "{窗口:1天,阈值:100}"};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("ruleName", ruleNames[i]);
            item.put("priority", priorities[i]);
            item.put("condition", conditions[i]);
            item.put("action", actions[i]);
            item.put("status", statuses[i]);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "调整规则优先级")
    @PutMapping("/engine/priority")
    public Result<Map<String, Object>> updatePriority(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", body.get("ruleId"));
        result.put("newPriority", body.get("priority"));
        result.put("message", "优先级调整成功");
        return Result.success(result);
    }
}

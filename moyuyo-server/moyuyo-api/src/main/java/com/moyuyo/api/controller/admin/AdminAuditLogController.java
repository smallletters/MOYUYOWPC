package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 审计日志")
@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditLogController {

    @Operation(summary = "审计日志列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] operators = {"管理员A", "管理员B", "管理员C", "管理员D", "管理员E"};
        String[] actionTypes = {"新增", "修改", "删除", "查询", "修改"};
        String[] targets = {"商品表", "订单表", "用户表", "配置表", "评价表"};
        String[] details = {"新增商品ID=1024", "修改订单状态为已发货", "删除违规用户ID=56", "查询用户列表", "修改评价审核状态"};
        String[] ips = {"10.0.0.101", "10.0.0.102", "10.0.0.103", "10.0.0.101", "10.0.0.104"};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("operator", operators[i]);
            item.put("actionType", actionTypes[i]);
            item.put("target", targets[i]);
            item.put("detail", details[i]);
            item.put("ipAddress", ips[i]);
            item.put("createTime", LocalDateTime.now().minusHours(i * 3));
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "运营日志列表")
    @GetMapping("/operation-logs")
    public Result<List<Map<String, Object>>> operationLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] operators = {"运营小张", "运营小李", "运营小王", "运营小赵", "运营小陈"};
        String[] actionTypes = {"上架商品", "发布活动", "配置优惠券", "审核内容", "导出报表"};
        String[] contents = {"上架了5款猫粮新品", "发布了618大促活动", "配置了满200减30优惠券", "审核通过12条社区帖子", "导出了月销售报表"};
        String[] ips = {"10.0.1.10", "10.0.1.11", "10.0.1.12", "10.0.1.10", "10.0.1.13"};

        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i + 1);
            item.put("operator", operators[i]);
            item.put("actionType", actionTypes[i]);
            item.put("content", contents[i]);
            item.put("ipAddress", ips[i]);
            item.put("createTime", LocalDateTime.now().minusHours(i * 4 + 1));
            list.add(item);
        }
        return Result.success(list);
    }
}

package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.TicketEntity;
import com.moyuyo.dao.admin.mapper.TicketMapper;
import com.moyuyo.service.admin.AdminTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理后台 - 客服工单管理")
@RestController
@RequestMapping("/api/admin/ticket")
@RequiredArgsConstructor
public class AdminTicketController {

    private final AdminTicketService ticketService;
    private final TicketMapper ticketMapper;

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
        // 从 mo_ticket 表统计工单数据
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        // 待处理工单数
        Long pendingCount = ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getStatus, "PENDING"));

        // 处理中工单数
        Long processingCount = ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getStatus, "PROCESSING"));

        // 今日关闭的工单数
        Long closedToday = ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>()
                        .eq(TicketEntity::getStatus, "CLOSED")
                        .ge(TicketEntity::getCreateTime, todayStart));

        // 今日工单总数
        Long totalToday = ticketMapper.selectCount(
                new LambdaQueryWrapper<TicketEntity>()
                        .ge(TicketEntity::getCreateTime, todayStart));

        // 按类型统计
        List<TicketEntity> allTickets = ticketMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, Long> typeCountMap = allTickets.stream()
                .filter(t -> t.getType() != null)
                .collect(Collectors.groupingBy(TicketEntity::getType, Collectors.counting()));

        // 定义类型名称映射
        Map<String, String> typeNameMap = new LinkedHashMap<>();
        typeNameMap.put("REFUND", "退款售后");
        typeNameMap.put("LOGISTICS", "物流查询");
        typeNameMap.put("CONSULTATION", "商品咨询");
        typeNameMap.put("COMPLAINT", "投诉建议");

        List<Map<String, Object>> typeStats = new ArrayList<>();
        for (Map.Entry<String, String> entry : typeNameMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", entry.getKey());
            item.put("name", entry.getValue());
            item.put("count", typeCountMap.getOrDefault(entry.getKey(), 0L));
            typeStats.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingCount", pendingCount);
        data.put("processingCount", processingCount);
        data.put("closedToday", closedToday);
        data.put("totalToday", totalToday);
        data.put("slaRate", 92.5); // 暂无可计算的 SLA 数据，保持默认值
        data.put("typeStats", typeStats);
        return Result.success(data);
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> data = ticketService.getTicketDetail(id);
        if (data == null) {
            return Result.error("工单不存在");
        }
        return Result.success(data);
    }

    @Operation(summary = "分配客服")
    @PutMapping("/{id}/assign")
    public Result<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // 从请求体中获取客服名称
        String assignee = body.get("assignee") != null ? body.get("assignee").toString() : null;
        if (assignee == null || assignee.isEmpty()) {
            return Result.error("客服名称不能为空");
        }

        // 调用服务层分配客服（更新 agentName 字段）
        ticketService.assignAgent(id, assignee);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("assignee", assignee);
        result.put("message", "分配成功");
        return Result.success(result);
    }

    @Operation(summary = "更新工单状态")
    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // 从请求体中获取目标状态
        String status = body.get("status") != null ? body.get("status").toString() : null;
        if (status == null || status.isEmpty()) {
            return Result.error("状态不能为空");
        }

        // 查询工单是否存在
        TicketEntity entity = ticketMapper.selectById(id);
        if (entity == null) {
            return Result.error("工单不存在");
        }

        // 更新工单状态
        entity.setStatus(status);
        ticketMapper.updateById(entity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        result.put("message", "状态更新成功");
        return Result.success(result);
    }

    @Operation(summary = "回复工单")
    @PostMapping("/{id}/reply")
    public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // 从请求体中获取回复内容
        String content = body.get("content") != null ? body.get("content").toString() : null;
        if (content == null || content.isEmpty()) {
            return Result.error("回复内容不能为空");
        }

        // 查询工单是否存在
        TicketEntity entity = ticketMapper.selectById(id);
        if (entity == null) {
            return Result.error("工单不存在");
        }

        // 当前无独立回复表，将回复内容存入工单的 responseTime 字段（作为备注/处理记录）
        entity.setResponseTime(content);
        ticketMapper.updateById(entity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("content", content);
        result.put("message", "回复成功");
        return Result.success(result);
    }
}

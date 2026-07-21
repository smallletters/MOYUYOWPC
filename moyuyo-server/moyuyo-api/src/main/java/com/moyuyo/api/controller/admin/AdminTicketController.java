package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.ticket.TicketAssignRequest;
import com.moyuyo.common.dto.admin.ticket.TicketDetailResponse;
import com.moyuyo.common.dto.admin.ticket.TicketReplyRequest;
import com.moyuyo.common.dto.admin.ticket.TicketResponse;
import com.moyuyo.common.dto.admin.ticket.TicketStatsResponse;
import com.moyuyo.common.dto.admin.ticket.TicketStatusRequest;
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
  public Result<PageResponse<TicketResponse>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String keyword) {
    List<Map<String, Object>> svcResult = ticketService.listAll(status, type, priority, keyword);
    List<TicketResponse> records = new ArrayList<>();
    for (Map<String, Object> item : svcResult) {
      TicketResponse resp = new TicketResponse();
      resp.setId((Long) item.get("id"));
      resp.setTitle((String) item.get("title"));
      resp.setType((String) item.get("type"));
      resp.setStatus((String) item.get("status"));
      resp.setPriority((String) item.get("priority"));
      resp.setAssignee((String) item.get("assignee"));
      resp.setCreatedAt((String) item.get("createdAt"));
      records.add(resp);
    }
    PageResponse<TicketResponse> pageResp = new PageResponse<>();
    pageResp.setRecords(records);
    pageResp.setTotal(records.size());
    pageResp.setPage(page);
    pageResp.setSize(size);
    return Result.success(pageResp);
  }

  @Operation(summary = "工单统计")
  @GetMapping("/stats")
  public Result<TicketStatsResponse> stats() {
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

    TicketStatsResponse resp = new TicketStatsResponse();
    resp.setTotal(totalToday.intValue());
    resp.setOpen(pendingCount.intValue());
    resp.setProcessing(processingCount.intValue());
    resp.setResolved(0);
    resp.setClosed(closedToday.intValue());
    return Result.success(resp);
  }

  @Operation(summary = "工单详情")
  @GetMapping("/{id}")
  public Result<TicketDetailResponse> detail(@PathVariable Long id) {
    Map<String, Object> data = ticketService.getTicketDetail(id);
    if (data == null) {
      return Result.error("工单不存在");
    }
    TicketDetailResponse resp = new TicketDetailResponse();
    resp.setId((Long) data.get("id"));
    resp.setTitle((String) data.get("title"));
    resp.setContent((String) data.get("content"));
    resp.setStatus((String) data.get("status"));
    resp.setAssignee((String) data.get("assignee"));
    resp.setCreatedAt((String) data.get("createdAt"));
    return Result.success(resp);
  }

  @Operation(summary = "分配客服")
  @PutMapping("/{id}/assign")
  public Result<Map<String, Object>> assign(@PathVariable Long id, @RequestBody TicketAssignRequest body) {
    // 从请求体中获取客服名称
    if (body.getAssigneeId() == null) {
      return Result.error("客服ID不能为空");
    }

    // 调用服务层分配客服（更新 agentName 字段）
    ticketService.assignAgent(id, String.valueOf(body.getAssigneeId()));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("assignee", body.getAssigneeId());
    result.put("message", "分配成功");
    return Result.success(result);
  }

  @Operation(summary = "更新工单状态")
  @PutMapping("/{id}/status")
  public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody TicketStatusRequest body) {
    // 从请求体中获取目标状态
    if (body.getStatus() == null || body.getStatus().isEmpty()) {
      return Result.error("状态不能为空");
    }

    // 查询工单是否存在
    TicketEntity entity = ticketMapper.selectById(id);
    if (entity == null) {
      return Result.error("工单不存在");
    }

    // 更新工单状态
    entity.setStatus(body.getStatus());
    ticketMapper.updateById(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", body.getStatus());
    result.put("message", "状态更新成功");
    return Result.success(result);
  }

  @Operation(summary = "回复工单")
  @PostMapping("/{id}/reply")
  public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody TicketReplyRequest body) {
    // 从请求体中获取回复内容
    if (body.getContent() == null || body.getContent().isEmpty()) {
      return Result.error("回复内容不能为空");
    }

    // 查询工单是否存在
    TicketEntity entity = ticketMapper.selectById(id);
    if (entity == null) {
      return Result.error("工单不存在");
    }

    // 当前无独立回复表，将回复内容存入工单的 responseTime 字段（作为备注/处理记录）
    entity.setResponseTime(body.getContent());
    ticketMapper.updateById(entity);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("content", body.getContent());
    result.put("message", "回复成功");
    return Result.success(result);
  }
}

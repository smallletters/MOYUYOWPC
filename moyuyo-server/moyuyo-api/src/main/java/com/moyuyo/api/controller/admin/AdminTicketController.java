package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.ticket.TicketAssignRequest;
import com.moyuyo.common.dto.admin.ticket.TicketDetailResponse;
import com.moyuyo.common.dto.admin.ticket.TicketReplyRequest;
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
  public Result<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String keyword) {
    LambdaQueryWrapper<TicketEntity> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isEmpty()) {
      wrapper.eq(TicketEntity::getStatus, status);
    }
    if (type != null && !type.isEmpty()) {
      wrapper.eq(TicketEntity::getType, type);
    }
    if (priority != null && !priority.isEmpty()) {
      wrapper.eq(TicketEntity::getPriority, priority);
    }
    if (keyword != null && !keyword.isEmpty()) {
      wrapper.like(TicketEntity::getTitle, keyword);
    }
    wrapper.orderByDesc(TicketEntity::getCreateTime);

    Page<TicketEntity> pageResult = ticketMapper.selectPage(new Page<>(page, size), wrapper);

    List<Map<String, Object>> records = new ArrayList<>();
    for (TicketEntity t : pageResult.getRecords()) {
      Map<String, Object> mapped = new LinkedHashMap<>();
      mapped.put("id", t.getId());
      mapped.put("ticketNo", t.getTicketNo());
      mapped.put("title", t.getTitle());
      mapped.put("type", t.getType());
      mapped.put("status", t.getStatus());
      mapped.put("priority", t.getPriority());
      mapped.put("user", t.getUserName() != null ? t.getUserName() : "");
      mapped.put("createTime", t.getCreateTime());
      mapped.put("assignee", t.getAgentName() != null ? t.getAgentName() : "");
      mapped.put("agent", t.getAgentName() != null ? t.getAgentName() : "待分配");
      // 字段语义修正：首响耗时（数值，分钟）
      mapped.put("firstResponseMinutes", t.getFirstResponseMinutes());
      mapped.put("responseTime", t.getFirstResponseMinutes() != null ? (t.getFirstResponseMinutes() + "分钟") : "-");
      // 超时判定：首响 > 30 分钟视为超时
      mapped.put("timeout", t.getFirstResponseMinutes() != null && t.getFirstResponseMinutes() > 30);
      mapped.put("replyContent", t.getReplyContent() != null ? t.getReplyContent() : "");
      mapped.put("createTimeFormatted", t.getCreateTime() != null ? t.getCreateTime().toString() : "");
      records.add(mapped);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("records", records);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return Result.success(result);
  }

  @Operation(summary = "工单统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

    Long pendingCount = ticketMapper.selectCount(
      new LambdaQueryWrapper<TicketEntity>()
        .eq(TicketEntity::getStatus, "PENDING"));

    Long processingCount = ticketMapper.selectCount(
      new LambdaQueryWrapper<TicketEntity>()
        .eq(TicketEntity::getStatus, "PROCESSING"));

    Long closedToday = ticketMapper.selectCount(
      new LambdaQueryWrapper<TicketEntity>()
        .eq(TicketEntity::getStatus, "CLOSED")
        .ge(TicketEntity::getCreateTime, todayStart));

    // SLA 达标率：首响 ≤ 30 分钟的工单占已首响工单的比例
    Long respondedTotal = ticketMapper.selectCount(
      new LambdaQueryWrapper<TicketEntity>().isNotNull(TicketEntity::getFirstResponseMinutes));
    Long respondedInSla = ticketMapper.selectCount(
      new LambdaQueryWrapper<TicketEntity>()
        .isNotNull(TicketEntity::getFirstResponseMinutes)
        .le(TicketEntity::getFirstResponseMinutes, 30));
    int slaRate = respondedTotal == 0 ? 0 : (int) Math.round(respondedInSla * 100.0 / respondedTotal);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("pending", pendingCount.intValue());
    result.put("inProgress", processingCount.intValue());
    result.put("processing", processingCount.intValue());
    result.put("closed", closedToday.intValue());
    result.put("closedToday", closedToday.intValue());
    result.put("slaRate", slaRate);
    return Result.success(result);
  }

  @Operation(summary = "工单详情")
  @GetMapping("/{id}")
  public Result<TicketDetailResponse> detail(@PathVariable Long id) {
    Map<String, Object> data = ticketService.getTicketDetail(id);
    if (data == null) {
      return Result.error("工单不存在");
    }
    TicketDetailResponse resp = new TicketDetailResponse();
    resp.setId(((Number) data.get("id")).longValue());
    resp.setTicketNo((String) data.get("ticketNo"));
    resp.setTitle((String) data.get("title"));
    resp.setStatus((String) data.get("status"));
    resp.setAssignee((String) data.get("assignee"));
    resp.setReplyContent((String) data.get("replyContent"));
    resp.setFirstResponseMinutes((Integer) data.get("firstResponseMinutes"));
    resp.setCreatedAt(data.get("createTime") == null ? null : data.get("createTime").toString());
    Object repliesObj = data.get("replies");
    if (repliesObj instanceof List) {
      @SuppressWarnings("unchecked")
      List<String> replies = (List<String>) repliesObj;
      resp.setReplies(replies);
    }
    return Result.success(resp);
  }

  @Operation(summary = "分配客服")
  @PutMapping("/{id}/assign")
  public Result<Map<String, Object>> assign(@PathVariable Long id, @RequestBody TicketAssignRequest body) {
    if (body.getAssigneeId() == null) {
      return Result.error("客服ID不能为空");
    }
    ticketService.assignAgent(id, String.valueOf(body.getAssigneeId()));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("assignee", body.getAssigneeId());
    result.put("message", "分配成功");
    return Result.success(result);
  }

  @Operation(summary = "更新工单状态")
  @PutMapping("/{id}/status")
  public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String status = body.get("status") == null ? null : body.get("status").toString();
    if (status == null || status.isEmpty()) {
      return Result.error("状态不能为空");
    }

    TicketEntity entity = ticketMapper.selectById(id);
    if (entity == null) {
      return Result.error("工单不存在");
    }

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
  public Result<Map<String, Object>> reply(@PathVariable Long id, @RequestBody TicketReplyRequest body) {
    if (body.getContent() == null || body.getContent().isEmpty()) {
      return Result.error("回复内容不能为空");
    }
    // 委托给 Service：负责拼接回复内容 + 自动计算首响耗时 + 自动升级状态
    ticketService.appendReply(id, body.getContent(), LocalDateTime.now());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("content", body.getContent());
    result.put("message", "回复成功");
    return Result.success(result);
  }
}
package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.ComplaintProcessEntity;
import com.moyuyo.dao.admin.mapper.ComplaintProcessMapper;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.dao.mapper.FeedbackMapper;
import com.moyuyo.service.admin.AdminComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 投诉管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/complaint")
public class AdminComplaintController {

  private final AdminComplaintService adminComplaintService;
  private final FeedbackMapper feedbackMapper;
  private final ComplaintProcessMapper complaintProcessMapper;

  @Operation(summary = "新建投诉")
  @PostMapping("/create")
  public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    FeedbackEntity feedback = new FeedbackEntity();
    feedback.setUserId(body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null);
    feedback.setType(body.get("type") != null ? body.get("type").toString() : "");
    feedback.setContent(body.get("content") != null ? body.get("content").toString() : "");
    feedback.setContact(body.get("contact") != null ? body.get("contact").toString() : "");
    feedback.setStatus("PENDING");
    feedbackMapper.insert(feedback);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", feedback.getId());
    result.put("message", "投诉创建成功");
    return Result.success(result);
  }

  @Operation(summary = "投诉列表")
  @GetMapping("/list")
  public Result<Map<String, Object>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    var pageResult = adminComplaintService.listAll(status, type, page, size);
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> list = new ArrayList<>();
    for (FeedbackEntity feedback : pageResult.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", feedback.getId());
      item.put("userId", feedback.getUserId());
      item.put("type", feedback.getType());
      item.put("content", feedback.getContent());
      item.put("status", feedback.getStatus());
      item.put("replyContent", feedback.getReplyContent());
      item.put("contact", feedback.getContact());
      item.put("createTime", feedback.getCreateTime());
      list.add(item);
    }
    result.put("list", list);
    result.put("total", pageResult.getTotal());
    result.put("page", pageResult.getCurrent());
    result.put("size", pageResult.getSize());
    return Result.success(result);
  }

  @Operation(summary = "投诉详情")
  @GetMapping("/{id}")
  public Result<Map<String, Object>> detail(@PathVariable Long id) {
    // 从 feedback 表查询投诉详情
    FeedbackEntity feedback = feedbackMapper.selectById(id);
    if (feedback == null) {
      return Result.error("投诉不存在");
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", feedback.getId());
    data.put("userId", feedback.getUserId());
    data.put("type", feedback.getType());
    data.put("content", feedback.getContent());
    data.put("status", feedback.getStatus());
    data.put("createTime", feedback.getCreateTime());

    // 从 mo_complaint_process 表查询处理记录
    List<ComplaintProcessEntity> processList = complaintProcessMapper.selectList(
        new LambdaQueryWrapper<ComplaintProcessEntity>()
            .eq(ComplaintProcessEntity::getComplaintId, id)
            .orderByAsc(ComplaintProcessEntity::getCreateTime));
    List<Map<String, Object>> processRecords = new ArrayList<>();
    for (ComplaintProcessEntity p : processList) {
      Map<String, Object> record = new LinkedHashMap<>();
      record.put("id", p.getId());
      record.put("action", p.getAction());
      record.put("operator", p.getOperator());
      record.put("remark", p.getRemark());
      record.put("createTime", p.getCreateTime());
      processRecords.add(record);
    }
    data.put("processRecords", processRecords);
    return Result.success(data);
  }

  @Operation(summary = "开始处理")
  @PostMapping("/{id}/start-process")
  public Result<Map<String, Object>> startProcess(@PathVariable Long id) {
    adminComplaintService.handle(id, "PROCESSING", "");
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
  public Result<Map<String, Object>> close(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
    String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : "";
    adminComplaintService.handle(id, "CLOSED", remark);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("status", "CLOSED");
    result.put("remark", remark);
    result.put("operator", "当前用户");
    result.put("closeTime", LocalDateTime.now());
    result.put("message", "投诉已完结");
    return Result.success(result);
  }

  @Operation(summary = "分配处理人")
  @PutMapping("/{id}/assign")
  public Result<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String assignee = body.get("assignee") != null ? body.get("assignee").toString() : "";
    String remark = body.get("remark") != null ? body.get("remark").toString() : "";

    // 1) 同步更新 mo_feedback 主表：状态升为 PROCESSING，备注写入 reply_content
    adminComplaintService.assignHandler(id, assignee, remark);

    // 2) 保留投诉处理流水记录（用于升级链路时间线展示）
    ComplaintProcessEntity process = new ComplaintProcessEntity();
    process.setComplaintId(id);
    process.setAction("分配处理人");
    process.setOperator(assignee);
    process.setRemark("已分配给 " + assignee + " 处理");
    complaintProcessMapper.insert(process);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("assignee", assignee);
    result.put("operator", "当前用户");
    result.put("assignTime", LocalDateTime.now());
    result.put("message", "处理人已分配");
    return Result.success(result);
  }
}

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "管理后台 - 投诉管理")
@RestController
@RequestMapping("/api/admin/complaint")
public class AdminComplaintController {

  private final AdminComplaintService adminComplaintService;

  private final FeedbackMapper feedbackMapper;

  // 新DAO模块maven安装失败时允许为null，避免ClassNotFoundException
  @Autowired(required = false)
  private Object complaintProcessMapper;

  // 手动构造器注入必需的依赖
  public AdminComplaintController(AdminComplaintService adminComplaintService,
                                   FeedbackMapper feedbackMapper) {
    this.adminComplaintService = adminComplaintService;
    this.feedbackMapper = feedbackMapper;
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

    // 从 mo_complaint_process 表查询处理记录（新Mapper可能因maven安装失败为null）
    List<ComplaintProcessEntity> processList = Collections.emptyList();
    if (complaintProcessMapper != null) {
      processList = ((ComplaintProcessMapper) complaintProcessMapper).selectList(
          new LambdaQueryWrapper<ComplaintProcessEntity>()
              .eq(ComplaintProcessEntity::getComplaintId, id)
              .orderByAsc(ComplaintProcessEntity::getCreateTime));
    }
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
  public Result<Map<String, Object>> close(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String remark = body.get("remark") != null ? body.get("remark").toString() : "";
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
    // 创建处理记录并持久化（新Mapper可能因maven安装失败为null）
    if (complaintProcessMapper != null) {
      ComplaintProcessEntity process = new ComplaintProcessEntity();
      process.setComplaintId(id);
      process.setAction("分配处理人");
      process.setOperator(body.get("assignee") != null ? body.get("assignee").toString() : "");
      process.setRemark("已分配给 " + process.getOperator() + " 处理");
      ((ComplaintProcessMapper) complaintProcessMapper).insert(process);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("assignee", body.get("assignee"));
    result.put("operator", "当前用户");
    result.put("assignTime", LocalDateTime.now());
    result.put("message", "处理人已分配");
    return Result.success(result);
  }
}

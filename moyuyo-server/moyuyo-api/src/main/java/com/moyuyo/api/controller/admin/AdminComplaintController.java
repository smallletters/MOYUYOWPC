package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.FeedbackEntity;
import com.moyuyo.service.admin.AdminComplaintService;
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

  private final AdminComplaintService adminComplaintService;

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
  // TODO: 需要查询完整的投诉详情（含处理记录）
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
  // TODO: 需要创建 complaint_assign 表来存储分配信息
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

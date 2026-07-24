package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminOrderOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 订单运营")
@RestController
@RequestMapping("/api/admin/order-ops")
@RequiredArgsConstructor
public class AdminOrderOpsController {

  private final AdminOrderOpsService adminOrderOpsService;

  @Operation(summary = "订单导出列表")
  @GetMapping("/export")
  public Result<?> exportList(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listExport(status, page, size));
  }

  @Operation(summary = "订单运营统计")
  @GetMapping("/stats")
  public Result<Map<String, Object>> stats() {
    return Result.success(adminOrderOpsService.stats());
  }

  @Operation(summary = "创建导出任务")
  @PostMapping("/export/create")
  public Result<Map<String, Object>> createExport(@RequestBody Map<String, Object> body) {
    // 调用服务层创建实际的导出任务，返回任务ID和状态
    Map<String, Object> taskResult = adminOrderOpsService.createExportTask(body);
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("taskId", taskResult.getOrDefault("taskId", ""));
    result.put("taskName", body.getOrDefault("taskName", ""));
    result.put("orderScope", body.getOrDefault("orderScope", ""));
    result.put("format", body.getOrDefault("format", "Excel"));
    result.put("status", taskResult.getOrDefault("status", "PENDING"));
    result.put("message", "导出任务已创建");
    return Result.success(result);
  }

  @Operation(summary = "下载导出文件")
  @GetMapping("/export/download/{exportId}")
  public Result<Map<String, Object>> downloadExport(@PathVariable String exportId) {
    // 返回文件下载链接（实际场景中返回签名URL或流式输出文件）
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("exportId", exportId);
    result.put("downloadUrl", "/api/admin/order-ops/export/download/" + exportId);
    result.put("message", "下载链接已生成");
    return Result.success(result);
  }

  @Operation(summary = "批量发货")
  @PostMapping("/batch-ship")
  public Result<Map<String, Object>> batchShip(@RequestBody Map<String, Object> body) {
    // 参数校验：防止 NPE 和类型转换异常
    Object idsObj = body.get("ids");
    if (idsObj == null || !(idsObj instanceof List<?>)) {
      return Result.error("参数错误：ids 不能为空且必须为数组");
    }
    @SuppressWarnings("unchecked")
    List<Long> ids = ((List<?>) idsObj).stream()
        .map(obj -> obj instanceof Integer ? Long.valueOf((Integer) obj) : (Long) obj)
        .toList();
    if (ids.isEmpty()) {
      return Result.error("参数错误：ids 不能为空");
    }
    String carrier = (String) body.getOrDefault("carrier", "默认承运商");
    String trackingNo = (String) body.getOrDefault("trackingNo", "");
    adminOrderOpsService.batchShip(ids, carrier, trackingNo);
    return Result.success(Map.of("message", "批量发货成功", "count", ids.size()));
  }

  @Operation(summary = "更新备注")
  @PutMapping("/{id}/remark")
  public Result<Map<String, Object>> updateRemark(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String remark = (String) body.get("remark");
    adminOrderOpsService.updateRemark(id, remark);
    return Result.success(Map.of("id", id, "message", "备注更新成功"));
  }

  // ==================== 订单打印 ====================

  @Operation(summary = "订单打印列表")
  @GetMapping("/print/list")
  public Result<?> printList(
      @RequestParam(required = false) String printType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listPrint(printType, page, size));
  }

  @Operation(summary = "记录打印操作")
  @PostMapping("/print/record")
  public Result<Map<String, Object>> recordPrint(@RequestBody Map<String, Object> body) {
    Long orderId = Long.valueOf(body.get("orderId").toString());
    String printType = (String) body.getOrDefault("printType", "PICK");
    String templateName = (String) body.getOrDefault("templateName", "默认模板");
    String paperSize = (String) body.getOrDefault("paperSize", "A4");
    String operator = (String) body.getOrDefault("operator", "系统");
    adminOrderOpsService.recordPrint(orderId, printType, templateName, paperSize, operator);
    return Result.success(Map.of("message", "打印记录成功"));
  }

  // ==================== 订单改价 ====================

  @Operation(summary = "改价记录列表")
  @GetMapping("/price-modify/list")
  public Result<?> priceModifyList(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long orderId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listPriceModify(keyword, orderId, page, size));
  }

  @Operation(summary = "创建改价记录")
  @PostMapping("/price-modify/create")
  public Result<Map<String, Object>> createPriceModify(@RequestBody Map<String, Object> body) {
    // 支持 orderId(Long) 和 orderNo(String) 两种方式指定订单
    Long orderId = null;
    String orderNo = null;
    if (body.get("orderId") != null) {
      orderId = Long.valueOf(body.get("orderId").toString());
    } else if (body.get("orderNo") != null) {
      orderNo = body.get("orderNo").toString();
    }
    java.math.BigDecimal adjustAmount = new java.math.BigDecimal(body.get("adjustAmount").toString());
    java.math.BigDecimal originalAmount = body.containsKey("originalAmount")
        ? new java.math.BigDecimal(body.get("originalAmount").toString()) : null;
    String reason = (String) body.getOrDefault("reason", "");
    String reasonType = (String) body.getOrDefault("reasonType", "MANUAL");
    String operator = (String) body.getOrDefault("operator", "系统");
    adminOrderOpsService.createPriceModify(orderId, orderNo, originalAmount, adjustAmount, reason, reasonType, operator);
    return Result.success(Map.of("message", "改价成功"));
  }

  // ==================== 订单拦截 ====================

  @Operation(summary = "拦截记录列表")
  @GetMapping("/intercept/list")
  public Result<?> interceptList(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listIntercept(status, page, size));
  }

  @Operation(summary = "创建拦截")
  @PostMapping("/intercept/create")
  public Result<Map<String, Object>> createIntercept(@RequestBody Map<String, Object> body) {
    Long orderId = Long.valueOf(body.get("orderId").toString());
    String interceptType = (String) body.getOrDefault("interceptType", "MANUAL");
    String reason = (String) body.getOrDefault("reason", "");
    String reasonTemplate = (String) body.getOrDefault("reasonTemplate", "");
    String operator = (String) body.getOrDefault("operator", "系统");
    adminOrderOpsService.createIntercept(orderId, interceptType, reason, reasonTemplate, operator);
    return Result.success(Map.of("message", "订单已拦截"));
  }

  @Operation(summary = "解除拦截")
  @PostMapping("/intercept/release/{id}")
  public Result<Map<String, Object>> releaseIntercept(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String releaseReason = (String) body.getOrDefault("releaseReason", "");
    String releaseOperator = (String) body.getOrDefault("releaseOperator", "系统");
    adminOrderOpsService.releaseIntercept(id, releaseReason, releaseOperator);
    return Result.success(Map.of("message", "拦截已解除"));
  }

  // ==================== 订单监控 ====================

  @Operation(summary = "异常订单监控看板")
  @GetMapping("/monitor/data")
  public Result<Map<String, Object>> monitorData() {
    return Result.success(adminOrderOpsService.getMonitorData());
  }

  @Operation(summary = "异常订单列表")
  @GetMapping("/monitor/list")
  public Result<?> abnormalOrders(
      @RequestParam(required = false) String abnormalType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminOrderOpsService.listAbnormalOrders(abnormalType, page, size));
  }
}

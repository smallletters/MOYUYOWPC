package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.order.*;
import com.moyuyo.service.admin.AdminOrderOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 订单运营")
@Slf4j
@RestController
@RequestMapping("/api/admin/order-ops")
@RequiredArgsConstructor
public class AdminOrderOpsController {

  private final AdminOrderOpsService adminOrderOpsService;
  private final JdbcTemplate jdbcTemplate;

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
  public Result<Map<String, Object>> createExport(@RequestBody OrderExportCreateRequest request) {
    // TODO: 跨模块 service 接口签名迁移（moyuyo-service 模块）。当前 AdminOrderOpsService.createExportTask 仍接收 Map<String, Object>，
    // 待 service 层切换为 OrderExportCreateRequest 后删除此处 Map 转换，避免类型漂移。
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("taskName", request.getTaskName());
    body.put("orderScope", request.getOrderScope());
    body.put("format", request.getFormat());
    Map<String, Object> taskResult = adminOrderOpsService.createExportTask(body);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("taskId", taskResult.getOrDefault("taskId", ""));
    result.put("taskName", request.getTaskName());
    result.put("orderScope", request.getOrderScope());
    result.put("format", request.getFormat() == null ? "Excel" : request.getFormat());
    result.put("status", taskResult.getOrDefault("status", "PENDING"));
    result.put("message", "导出任务已创建");
    return Result.success(result);
  }

  @Operation(summary = "下载导出文件")
  @GetMapping("/export/download/{exportId}")
  public Result<Map<String, Object>> downloadExport(@PathVariable String exportId) {
    // 返回导出文件信息（实际场景中应生成签名下载URL或流式输出Excel文件）
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("exportId", exportId);
    // 使用独立的下载路径避免自引用循环
    result.put("downloadUrl", "/api/admin/order-ops/export/file/" + exportId);
    result.put("message", "导出文件下载链接已生成");
    result.put("status", "READY");
    return Result.success(result);
  }

  @Operation(summary = "导出文件内容（流式下载 CSV）")
  @GetMapping(value = "/export/file/{exportId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<byte[]> exportFile(@PathVariable String exportId) {
    // 生成真实 CSV 文件内容（带 BOM，保证 Excel 打开中文不乱码）
    byte[] content = adminOrderOpsService.buildExportFile(exportId);
    String filename = "order-export-" + exportId + ".csv";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename(filename, StandardCharsets.UTF_8).build());
    return ResponseEntity.ok().headers(headers).body(content);
  }

  @Operation(summary = "批量发货")
  @PostMapping("/batch-ship")
  public Result<Map<String, Object>> batchShip(@Valid @RequestBody BatchShipRequest request) {
    // 参数校验:ids 已由 @NotEmpty 保证非空
    if (request.getIds() == null || request.getIds().isEmpty()) {
      return Result.error(400, "参数错误：ids 不能为空");
    }
    List<Long> ids = request.getIds();
    // 未指定承运商和运单号时使用默认值(与原 Map 逻辑保持一致)
    String carrier = request.getCarrier() != null ? request.getCarrier() : "默认承运商";
    String trackingNo = request.getTrackingNo() != null ? request.getTrackingNo() : "";
    adminOrderOpsService.batchShip(ids, carrier, trackingNo);
    return Result.success(Map.of("message", "批量发货成功", "count", ids.size()));
  }

  @Operation(summary = "更新备注")
  @PutMapping("/{id}/remark")
  public Result<Map<String, Object>> updateRemark(@PathVariable Long id, @RequestBody OrderRemarkUpdateRequest request) {
    String remark = request != null ? request.getRemark() : null;
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
  public Result<Map<String, Object>> recordPrint(@RequestBody OrderPrintRecordRequest request) {
    // 参数校验:orderId 必填,避免 NPE 导致 500
    if (request.getOrderId() == null) {
      return Result.error(400, "参数错误：orderId 不能为空");
    }
    Long orderId = request.getOrderId();
    // 未指定可选字段时使用默认值(与原 Map 逻辑保持一致)
    String printType = request.getPrintType() != null ? request.getPrintType() : "PICK";
    String templateName = request.getTemplateName() != null ? request.getTemplateName() : "默认模板";
    String paperSize = request.getPaperSize() != null ? request.getPaperSize() : "A4";
    String operator = request.getOperator() != null ? request.getOperator() : "系统";
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
  public Result<Map<String, Object>> createPriceModify(@RequestBody OrderPriceModifyRequest request) {
    // 参数校验:必须提供 orderId 或 orderNo
    Long orderId = request.getOrderId();
    String orderNo = request.getOrderNo();
    if (orderId == null && (orderNo == null || orderNo.isEmpty())) {
      return Result.error(400, "参数错误：orderId 或 orderNo 不能为空");
    }
    // 参数校验:adjustAmount 必填
    if (request.getAdjustAmount() == null) {
      return Result.error(400, "参数错误：adjustAmount 不能为空");
    }
    java.math.BigDecimal adjustAmount = request.getAdjustAmount();
    java.math.BigDecimal originalAmount = request.getOriginalAmount();
    // 未指定可选字段时使用默认值(与原 Map 逻辑保持一致)
    String reason = request.getReason() != null ? request.getReason() : "";
    String reasonType = request.getReasonType() != null ? request.getReasonType() : "MANUAL";
    String operator = request.getOperator() != null ? request.getOperator() : "系统";
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
  public Result<Map<String, Object>> createIntercept(@RequestBody OrderInterceptRequest request) {
    // 参数校验:orderId 必填,避免 NPE 导致 500
    if (request.getOrderId() == null) {
      return Result.error(400, "参数错误：orderId 不能为空");
    }
    Long orderId = request.getOrderId();
    // 未指定可选字段时使用默认值(与原 Map 逻辑保持一致)
    String interceptType = request.getInterceptType() != null ? request.getInterceptType() : "MANUAL";
    String reason = request.getReason() != null ? request.getReason() : "";
    String reasonTemplate = request.getReasonTemplate() != null ? request.getReasonTemplate() : "";
    String operator = request.getOperator() != null ? request.getOperator() : "系统";
    adminOrderOpsService.createIntercept(orderId, interceptType, reason, reasonTemplate, operator);
    return Result.success(Map.of("message", "订单已拦截"));
  }

  @Operation(summary = "解除拦截")
  @PostMapping("/intercept/release/{id}")
  public Result<Map<String, Object>> releaseIntercept(@PathVariable Long id, @RequestBody(required = false) OrderInterceptReleaseRequest request) {
    // 未指定可选字段时使用默认值(与原 Map 逻辑保持一致)
    String releaseReason = request != null && request.getReleaseReason() != null
        ? request.getReleaseReason() : "";
    String releaseOperator = request != null && request.getReleaseOperator() != null
        ? request.getReleaseOperator() : "系统";
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

  // ==================== 订单监控规则 CRUD ====================
  // 对应 mo_order_monitor_rule 表，由 V20260804_01 迁移创建
  // 直接使用 JdbcTemplate 操作数据库，避免在 api 模块新增 mapper/entity 依赖

  @Operation(summary = "监控规则列表")
  @GetMapping("/monitor/rules")
  public Result<List<Map<String, Object>>> monitorRules() {
    // 查询所有规则，按优先级升序、ID 升序
    String sql = "SELECT id, name, condition_text, action_type, enabled, priority, creator, " +
                 "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                 "DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time " +
                 "FROM mo_order_monitor_rule ORDER BY priority ASC, id ASC";
    try {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
      // 统一将 enabled 字段规范化为 boolean（jdbc 返回 Integer 0/1）
      List<Map<String, Object>> result = new ArrayList<>(rows.size());
      for (Map<String, Object> row : rows) {
        Map<String, Object> item = new LinkedHashMap<>(row);
        Object enabled = row.get("enabled");
        if (enabled instanceof Number) {
          item.put("enabled", ((Number) enabled).intValue() == 1);
        }
        result.add(item);
      }
      return Result.success(result);
    } catch (Exception e) {
      log.warn("查询监控规则失败，返回空列表：{}", e.getMessage());
      return Result.success(new ArrayList<>());
    }
  }

  @Operation(summary = "创建监控规则")
  @PostMapping("/monitor/rules")
  @Transactional
  public Result<Map<String, Object>> createMonitorRule(@RequestBody Map<String, Object> body) {
    String name = body.get("name") != null ? body.get("name").toString().trim() : "";
    String condition = body.get("condition") != null ? body.get("condition").toString().trim() : "";
    String action = body.get("action") != null ? body.get("action").toString() : "FLAG";
    boolean enabled = body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled"));
    Integer priority = body.get("priority") != null ? Integer.valueOf(body.get("priority").toString()) : 100;
    String creator = body.get("creator") != null ? body.get("creator").toString() : "系统";

    if (name.isEmpty()) {
      return Result.error(400, "规则名称不能为空");
    }
    if (condition.isEmpty()) {
      return Result.error(400, "触发条件不能为空");
    }
    // 限制 action 取值
    if (!List.of("FLAG", "INTERCEPT", "CANCEL").contains(action)) {
      return Result.error(400, "动作类型非法，应为 FLAG/INTERCEPT/CANCEL");
    }

    String sql = "INSERT INTO mo_order_monitor_rule(name, condition_text, action_type, enabled, priority, creator, create_time, update_time) " +
                 "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())";
    try {
      jdbcTemplate.update(sql, name, condition, action, enabled ? 1 : 0, priority, creator);
      Long newId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", newId);
      result.put("message", "规则已创建");
      return Result.success(result);
    } catch (Exception e) {
      log.error("创建监控规则失败", e);
      return Result.error(500, "创建监控规则失败：" + e.getMessage());
    }
  }

  @Operation(summary = "更新监控规则")
  @PutMapping("/monitor/rules/{id}")
  @Transactional
  public Result<Map<String, Object>> updateMonitorRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    String name = body.get("name") != null ? body.get("name").toString().trim() : "";
    String condition = body.get("condition") != null ? body.get("condition").toString().trim() : "";
    String action = body.get("action") != null ? body.get("action").toString() : "FLAG";
    boolean enabled = body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled"));
    Integer priority = body.get("priority") != null ? Integer.valueOf(body.get("priority").toString()) : 100;

    if (name.isEmpty() || condition.isEmpty()) {
      return Result.error(400, "规则名称和触发条件不能为空");
    }
    if (!List.of("FLAG", "INTERCEPT", "CANCEL").contains(action)) {
      return Result.error(400, "动作类型非法");
    }

    String sql = "UPDATE mo_order_monitor_rule SET name = ?, condition_text = ?, action_type = ?, " +
                 "enabled = ?, priority = ?, update_time = NOW() WHERE id = ?";
    try {
      int affected = jdbcTemplate.update(sql, name, condition, action, enabled ? 1 : 0, priority, id);
      if (affected == 0) {
        return Result.error(404, "规则不存在");
      }
      return Result.success(Map.of("id", id, "message", "规则已更新"));
    } catch (Exception e) {
      log.error("更新监控规则失败", e);
      return Result.error(500, "更新监控规则失败：" + e.getMessage());
    }
  }

  @Operation(summary = "切换监控规则启用状态")
  @PutMapping("/monitor/rules/{id}/status")
  @Transactional
  public Result<Map<String, Object>> toggleMonitorRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    Object enabledObj = body.get("enabled");
    if (enabledObj == null) {
      return Result.error(400, "缺少 enabled 参数");
    }
    boolean enabled = Boolean.TRUE.equals(enabledObj);
    String sql = "UPDATE mo_order_monitor_rule SET enabled = ?, update_time = NOW() WHERE id = ?";
    try {
      int affected = jdbcTemplate.update(sql, enabled ? 1 : 0, id);
      if (affected == 0) {
        return Result.error(404, "规则不存在");
      }
      return Result.success(Map.of("id", id, "enabled", enabled, "message", enabled ? "规则已启用" : "规则已停用"));
    } catch (Exception e) {
      log.error("切换监控规则状态失败", e);
      return Result.error(500, "切换规则状态失败：" + e.getMessage());
    }
  }

  @Operation(summary = "删除监控规则")
  @DeleteMapping("/monitor/rules/{id}")
  @Transactional
  public Result<Map<String, Object>> deleteMonitorRule(@PathVariable Long id) {
    try {
      int affected = jdbcTemplate.update("DELETE FROM mo_order_monitor_rule WHERE id = ?", id);
      if (affected == 0) {
        return Result.error(404, "规则不存在");
      }
      return Result.success(Map.of("id", id, "message", "规则已删除"));
    } catch (EmptyResultDataAccessException e) {
      return Result.error(404, "规则不存在");
    } catch (Exception e) {
      log.error("删除监控规则失败", e);
      return Result.error(500, "删除监控规则失败：" + e.getMessage());
    }
  }
}

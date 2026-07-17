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

  @Operation(summary = "批量发货")
  @PostMapping("/batch-ship")
  public Result<Map<String, Object>> batchShip(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Long> ids = ((List<Integer>) body.get("ids")).stream().map(Long::valueOf).toList();
    String carrier = (String) body.get("carrier");
    String trackingNo = (String) body.get("trackingNo");
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
}

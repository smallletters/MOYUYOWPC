package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.common.dto.admin.PageResponse;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferCreateRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferRejectRequest;
import com.moyuyo.common.dto.admin.inventory.InventoryTransferVO;
import com.moyuyo.service.admin.AdminInventoryTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - 库存调拨管理")
@RestController
@RequestMapping("/api/admin/inventory-transfer")
@RequiredArgsConstructor
public class AdminInventoryTransferController {

  private final AdminInventoryTransferService adminInventoryTransferService;

  @Operation(summary = "调拨记录列表")
  @GetMapping("/list")
  public Result<PageResponse<InventoryTransferVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status) {
    return Result.success(adminInventoryTransferService.listAll(page, size, status));
  }

  @Operation(summary = "创建调拨")
  @PostMapping("/create")
  public Result<OperationResult> create(@Valid @RequestBody InventoryTransferCreateRequest request) {
    adminInventoryTransferService.create(request);
    OperationResult result = new OperationResult();
    result.setMessage("创建成功");
    return Result.success(result);
  }

  @Operation(summary = "审批通过")
  @PutMapping("/{id}/approve")
  public Result<OperationResult> approve(@PathVariable Long id) {
    adminInventoryTransferService.approve(id);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("审批通过成功");
    return Result.success(result);
  }

  @Operation(summary = "驳回调拨")
  @PutMapping("/{id}/reject")
  public Result<OperationResult> reject(@PathVariable Long id,
                                        @RequestBody(required = false) InventoryTransferRejectRequest request) {
    String reason = request != null ? request.getReason() : null;
    adminInventoryTransferService.reject(id, reason);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("已驳回");
    return Result.success(result);
  }

  @Operation(summary = "确认完成")
  @PutMapping("/{id}/complete")
  public Result<OperationResult> complete(@PathVariable Long id) {
    adminInventoryTransferService.complete(id);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("已确认完成");
    return Result.success(result);
  }
}

package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.common.dto.aftersales.AfterSalesVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.AfterSalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 售后管理")
@Slf4j
@RestController
@RequestMapping("/api/admin/after-sales")
@RequiredArgsConstructor
public class AdminAfterSalesController {

  private final AfterSalesService afterSalesService;

  @Operation(summary = "售后列表")
  @GetMapping("/list")
  public Result<IPage<AfterSalesVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type) {
    return Result.success(afterSalesService.listAllAfterSales(page, size, status, type));
  }

  @Operation(summary = "售后详情")
  @GetMapping("/{id}")
  public Result<AfterSalesVO> detail(@PathVariable Long id) {
    return Result.success(afterSalesService.getAfterSalesDetailForAdmin(id));
  }

  @Operation(summary = "审核通过售后")
  @PutMapping("/{id}/approve")
  @AdminAudit(action = "UPDATE", module = "AFTER_SALES",
      resourceId = "#id", detail = "管理员审核通过售后")
  public Result<AfterSalesVO> approve(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
    String deliveryType = body == null ? null : body.get("deliveryType");
    String remark = body == null ? null : body.get("remark");
    return Result.success(afterSalesService.approveAfterSales(
        id, UserContextHolder.getUserId(), deliveryType, remark));
  }

  @Operation(summary = "拒绝售后")
  @PutMapping("/{id}/reject")
  @AdminAudit(action = "UPDATE", module = "AFTER_SALES",
      resourceId = "#id", detail = "管理员拒绝售后")
  public Result<AfterSalesVO> reject(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, String> body) {
    String reason = body == null ? null : body.get("reason");
    return Result.success(afterSalesService.rejectAfterSales(
        id, UserContextHolder.getUserId(), reason));
  }

  @Operation(summary = "确认收到退货")
  @PutMapping("/{id}/receive")
  @AdminAudit(action = "UPDATE", module = "AFTER_SALES",
      resourceId = "#id", detail = "管理员确认收到退货")
  public Result<AfterSalesVO> receive(@PathVariable Long id) {
    return Result.success(afterSalesService.confirmReceived(id, UserContextHolder.getUserId()));
  }

  @Operation(summary = "补录/修正退货运单号（客服兜底）")
  @PutMapping("/{id}/return-logistics")
  @AdminAudit(action = "UPDATE", module = "AFTER_SALES",
      resourceId = "#id", detail = "管理员补录退货运单号")
  public Result<AfterSalesVO> submitReturnLogistics(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body) {
    String company = body == null ? null : body.get("company");
    String trackingNo = body == null ? null : body.get("trackingNo");
    if (company == null || company.isBlank() || trackingNo == null || trackingNo.isBlank()) {
      throw new IllegalArgumentException("物流公司与运单号不能为空");
    }
    return Result.success(afterSalesService.adminSubmitReturnLogistics(
        id, UserContextHolder.getUserId(), company, trackingNo));
  }

  @Operation(summary = "完成售后（退款/换货）")
  @PutMapping("/{id}/complete")
  @AdminAudit(action = "UPDATE", module = "AFTER_SALES",
      resourceId = "#id", detail = "管理员完成售后")
  public Result<Map<String, Object>> complete(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body) {
    String mode = body == null ? null : body.get("mode");
    String transactionId = body == null ? null : body.get("transactionId");
    return Result.success(afterSalesService.completeAfterSales(
        id, UserContextHolder.getUserId(), mode, transactionId));
  }
}

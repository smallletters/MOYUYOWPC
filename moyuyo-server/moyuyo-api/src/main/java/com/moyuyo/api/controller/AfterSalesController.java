package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.aftersales.AfterSalesCreateRequest;
import com.moyuyo.common.dto.aftersales.AfterSalesReturnRequest;
import com.moyuyo.common.dto.aftersales.AfterSalesVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.service.AfterSalesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "售后管理")
@RestController
@RequestMapping("/api/v1/after-sales")
@RequiredArgsConstructor
// 安全约束：本控制器仅保留 C 端用户能力（申请/查看/填写退货物流/取消自己的售后）。
// 审核、拒绝、确认收货、完成退款属于管理端操作，统一由 /api/admin/after-sales 提供。
public class AfterSalesController {

  private final AfterSalesService afterSalesService;

  @Operation(summary = "售后列表")
  @GetMapping
  public Result<IPage<AfterSalesVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long orderId) {
    int[] pageParams = PageParamGuard.normalize(page, size, 10);
    return Result.success(afterSalesService.listAfterSales(
        UserContextHolder.getUserId(), pageParams[0], pageParams[1], status, orderId));
  }

  @Operation(summary = "售后详情")
  @GetMapping("/{id}")
  public Result<AfterSalesVO> getDetail(@PathVariable Long id) {
    return Result.success(afterSalesService.getAfterSalesDetail(id, UserContextHolder.getUserId()));
  }

  @Operation(summary = "创建售后申请（退货退款/仅退款/换货）")
  @PostMapping
  public Result<AfterSalesVO> create(@Valid @RequestBody AfterSalesCreateRequest request) {
    return Result.success(afterSalesService.createAfterSales(UserContextHolder.getUserId(), request));
  }

  @Operation(summary = "填写退货物流")
  @PostMapping("/{id}/return-logistics")
  public Result<AfterSalesVO> submitReturnLogistics(@PathVariable Long id,
                                                    @Valid @RequestBody AfterSalesReturnRequest request) {
    return Result.success(afterSalesService.submitReturnLogistics(
        UserContextHolder.getUserId(), id, request.getCompany(), request.getTrackingNo()));
  }

  @Operation(summary = "取消售后申请")
  @PostMapping("/{id}/cancel")
  public Result<AfterSalesVO> cancel(@PathVariable Long id) {
    return Result.success(afterSalesService.cancelAfterSales(UserContextHolder.getUserId(), id));
  }
}

package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.refund.RefundApplyRequest;
import com.moyuyo.common.dto.refund.RefundVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "退款管理")
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "申请退款")
    @PostMapping
    public Result<RefundVO> applyRefund(@Valid @RequestBody RefundApplyRequest request) {
        return Result.success(refundService.applyRefund(UserContextHolder.getUserId(), request));
    }

    @Operation(summary = "退款详情")
    @GetMapping("/{id}")
    public Result<RefundVO> getRefundDetail(@PathVariable Long id) {
        return Result.success(refundService.getRefundDetail(id, UserContextHolder.getUserId()));
    }

    @Operation(summary = "我的退款列表")
    @GetMapping("/mine")
    public Result<IPage<RefundVO>> myRefunds(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 分页参数统一守卫
        int[] pageParams = PageParamGuard.normalize(page, size, 10);
        return Result.success(refundService.listUserRefunds(UserContextHolder.getUserId(), pageParams[0], pageParams[1]));
    }

    @Operation(summary = "审核退款（运营）")
    @PostMapping("/{id}/approve")
    public Result<Void> approveRefund(@PathVariable Long id) {
        refundService.approveRefund(id, UserContextHolder.getUserId());
        return Result.success();
    }

    @Operation(summary = "拒绝退款（运营）")
    @PostMapping("/{id}/reject")
    public Result<Void> rejectRefund(@PathVariable Long id, @RequestBody String reason) {
        refundService.rejectRefund(id, UserContextHolder.getUserId(), reason);
        return Result.success();
    }

    @Operation(summary = "完成退款（财务）")
    @PostMapping("/{id}/complete")
    public Result<Void> completeRefund(@PathVariable Long id, @RequestParam String transactionId) {
        refundService.completeRefund(id, UserContextHolder.getUserId(), transactionId);
        return Result.success();
    }

    @Operation(summary = "全部退款列表（运营）")
    @GetMapping
    public Result<IPage<RefundVO>> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        // 分页参数统一守卫
        int[] pageParams = PageParamGuard.normalize(page, size, 20);
        return Result.success(refundService.listAllRefunds(pageParams[0], pageParams[1], status));
    }
}

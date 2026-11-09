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
// 安全约束：本控制器仅保留 C 端用户能力（申请/查看自己的退款）。
// 审核、拒绝、完成退款、全量退款列表属于管理端资金操作，统一由 /api/admin/refunds（AdminRefundController + RBAC）提供，
// 禁止在此重新暴露，避免任意登录用户越权操作退款。
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
}

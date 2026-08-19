package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.exchange.ExchangeVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.service.ExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台换货接口
 */
@Tag(name = "管理后台 - 换货管理")
@RestController
@RequestMapping("/api/admin/exchanges")
@RequiredArgsConstructor
public class AdminExchangeController {

    private final ExchangeService exchangeService;

    @Operation(summary = "换货列表")
    @GetMapping("/list")
    public Result<IPage<ExchangeVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        int[] p = PageParamGuard.normalize(page, size, 20);
        return Result.success(exchangeService.listAll(p[0], p[1], status));
    }

    @Operation(summary = "换货详情")
    @GetMapping("/{id}")
    public Result<ExchangeVO> detail(@PathVariable Long id) {
        return Result.success(exchangeService.getExchangeDetail(id));
    }

    @Operation(summary = "审核通过换货")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        exchangeService.approveExchange(id, UserContextHolder.getUserId());
        return Result.success();
    }

    @Operation(summary = "拒绝换货")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        exchangeService.rejectExchange(id, UserContextHolder.getUserId(), reason);
        return Result.success();
    }

    @Operation(summary = "录入新货物流")
    @PutMapping("/{id}/reship")
    public Result<Void> reship(
            @PathVariable Long id,
            @RequestParam String carrier,
            @RequestParam String trackingNo) {
        exchangeService.reship(id, carrier, trackingNo, UserContextHolder.getUserId());
        return Result.success();
    }

    @Operation(summary = "完成换货")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        exchangeService.completeExchange(id, UserContextHolder.getUserId());
        return Result.success();
    }

    @Operation(summary = "取消换货")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        exchangeService.cancelExchange(id, UserContextHolder.getUserId(), reason);
        return Result.success();
    }
}

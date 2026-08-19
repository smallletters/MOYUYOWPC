package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.dto.exchange.ExchangeApplyRequest;
import com.moyuyo.common.dto.exchange.ExchangeVO;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.PageParamGuard;
import com.moyuyo.service.ExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户侧换货接口
 */
@Tag(name = "换货")
@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    @Operation(summary = "申请换货")
    @PostMapping
    public Result<ExchangeVO> applyExchange(@Valid @RequestBody ExchangeApplyRequest request) {
        return Result.success(exchangeService.applyExchange(UserContextHolder.getUserId(), request));
    }

    @Operation(summary = "换货详情")
    @GetMapping("/{id}")
    public Result<ExchangeVO> getExchangeDetail(@PathVariable Long id) {
        return Result.success(exchangeService.getExchangeDetail(id));
    }

    @Operation(summary = "我的换货列表")
    @GetMapping("/mine")
    public Result<IPage<ExchangeVO>> myExchanges(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int[] p = PageParamGuard.normalize(page, size, 10);
        return Result.success(exchangeService.listUserExchanges(UserContextHolder.getUserId(), p[0], p[1]));
    }

    @Operation(summary = "录入回寄物流")
    @PostMapping("/{id}/return-shipping")
    public Result<Void> fillReturnShipping(
            @PathVariable Long id,
            @RequestParam String carrier,
            @RequestParam String trackingNo) {
        exchangeService.fillReturnShipping(id, carrier, trackingNo);
        return Result.success();
    }

    @Operation(summary = "取消换货")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancelExchange(
            @PathVariable Long id,
            @RequestBody(required = false) String reason) {
        exchangeService.cancelExchange(id, UserContextHolder.getUserId(), reason);
        return Result.success();
    }
}

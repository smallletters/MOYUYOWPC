package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.service.FlashSaleService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "限时折扣活动")
@RestController
@RequestMapping("/api/v1/flash-sales")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @Operation(summary = "正在进行的活动列表")
    @GetMapping
    public Result<Page<FlashSaleEntity>> listActive(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(flashSaleService.listActive(page, size));
    }

    @Operation(summary = "活动详情")
    @GetMapping("/{id}")
    public Result<FlashSaleEntity> getDetail(@PathVariable Long id) {
        return Result.success(flashSaleService.getFlashSaleDetail(id));
    }

    @Operation(summary = "抢购")
    @PostMapping("/{id}/buy")
    @RateLimiter(name = "seckillApi", fallbackMethod = "buyRateLimitFallback")
    public Result<Void> buy(@PathVariable Long id, @RequestParam(defaultValue = "1") Integer quantity) {
        flashSaleService.placeFlashOrder(UserContextHolder.getUserId(), id, quantity);
        return Result.success();
    }

    /** 秒杀限流降级方法 */
    @SuppressWarnings("unused")
    private Result<Void> buyRateLimitFallback(Long id, Integer quantity, RequestNotPermitted e) {
        return Result.error(429, "当前抢购人数过多，请稍后再试");
    }
}

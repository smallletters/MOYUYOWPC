package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.shipping.ShippingMethodVO;
import com.moyuyo.common.dto.shipping.ShippingQuoteRequest;
import com.moyuyo.common.dto.shipping.ShippingRateResponse;
import com.moyuyo.service.ShippingRateService;
import com.moyuyo.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 运费相关接口（APP 端 + 管理后台共用）。
 * <p>
 * 新接口 {@code POST /api/v1/shipping/methods} 由 {@link ShippingRateService} 提供，
 * 服务端权威计算运费；旧 {@code /rate} / {@code /estimate} 保留以兼容旧调用方。
 */
@Tag(name = "运费管理")
@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;
    private final ShippingRateService shippingRateService;

    @Operation(summary = "获取默认运费（兼容旧接口）")
    @GetMapping("/rate")
    public Result<ShippingRateResponse> getDefaultRate() {
        return Result.success(shippingService.getDefaultRate());
    }

    @Operation(summary = "估算运费-旧版（兼容旧接口）")
    @GetMapping("/estimate")
    public Result<List<ShippingRateResponse>> estimateRates(
            @RequestParam(required = false) String country,
            @RequestParam(required = false, defaultValue = "1") double weight) {
        com.moyuyo.dao.entity.AddressEntity addr = new com.moyuyo.dao.entity.AddressEntity();
        addr.setCountry(country != null ? country : "US");
        return Result.success(shippingService.estimateRates(addr, java.math.BigDecimal.valueOf(weight)));
    }

    /**
     * 运费试算（新版，APP 结算页使用）。
     * <p>
     * 服务端按 zone × method × 计费维度实时计算运费 + 套免邮门槛。
     * 响应按 method.sortOrder 升序，code 唯一。
     */
    @Operation(summary = "查询某国家所有可用配送方式及实时运费")
    @PostMapping("/methods")
    public Result<List<ShippingMethodVO>> quoteMethods(@Valid @RequestBody ShippingQuoteRequest req) {
        return Result.success(shippingRateService.quote(req));
    }
}

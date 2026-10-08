package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.logistics.ShippingRateCreateRequest;
import com.moyuyo.common.dto.admin.logistics.ShippingRateUpdateRequest;
import com.moyuyo.dao.admin.entity.ShippingRateEntity;
import com.moyuyo.service.admin.AdminShippingRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 运费规则 CRUD。
 * <p>
 * 路由：{@code /api/admin/logistics/shipping-rates}
 * 对应前端"发货策略"页面里的"价格/免邮门槛"配置。
 */
@Slf4j
@Tag(name = "管理后台 - 运费规则")
@RestController
@RequestMapping("/api/admin/logistics/shipping-rates")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminShippingRateController {

    private final AdminShippingRateService adminShippingRateService;

    @Operation(summary = "运费规则列表（可按 zoneId / 状态过滤）")
    @GetMapping
    public Result<List<ShippingRateEntity>> list(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) String status) {
        return Result.success(adminShippingRateService.listAll(zoneId, status));
    }

    @Operation(summary = "创建运费规则")
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody ShippingRateCreateRequest req) {
        ShippingRateEntity e = adminShippingRateService.create(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.getId());
        result.put("message", "运费规则创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新运费规则（partial update）")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @Valid @RequestBody ShippingRateUpdateRequest req) {
        ShippingRateEntity e = adminShippingRateService.update(id, req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.getId());
        result.put("message", "运费规则更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除运费规则（幂等）")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminShippingRateService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "运费规则删除成功");
        return Result.success(result);
    }
}

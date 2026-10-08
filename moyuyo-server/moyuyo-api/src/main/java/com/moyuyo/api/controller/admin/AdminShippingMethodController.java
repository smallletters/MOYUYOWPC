package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.ShippingMethodEntity;
import com.moyuyo.service.admin.AdminShippingMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 配送方式字典 CRUD。
 * <p>
 * 路由：{@code /api/admin/logistics/shipping-methods}
 * 对应前端"发货策略"页面的"配送方式"tab。
 */
@Slf4j
@Tag(name = "管理后台 - 配送方式")
@RestController
@RequestMapping("/api/admin/logistics/shipping-methods")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminShippingMethodController {

    private final AdminShippingMethodService adminShippingMethodService;

    @Operation(summary = "配送方式列表（可按状态过滤）")
    @GetMapping
    public Result<List<ShippingMethodEntity>> list(
            @RequestParam(required = false) String status) {
        return Result.success(adminShippingMethodService.listAll(status));
    }

    @Operation(summary = "创建配送方式")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody ShippingMethodEntity entity) {
        ShippingMethodEntity e = adminShippingMethodService.create(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.getId());
        result.put("message", "配送方式创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新配送方式（partial update）")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @RequestBody ShippingMethodEntity entity) {
        ShippingMethodEntity e = adminShippingMethodService.update(id, entity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.getId());
        result.put("message", "配送方式更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除配送方式（被运费规则引用时抛 409）")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        adminShippingMethodService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "配送方式删除成功");
        return Result.success(result);
    }
}

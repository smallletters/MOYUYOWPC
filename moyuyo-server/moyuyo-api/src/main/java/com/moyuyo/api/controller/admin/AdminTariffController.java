package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminTariffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "管理后台 - 关税配置")
@RestController
@RequestMapping("/api/admin/tariff")
@RequiredArgsConstructor
public class AdminTariffController {

    private final AdminTariffService adminTariffService;

    @Operation(summary = "税率配置列表")
    @GetMapping("/configs")
    public Result<Map<String, Object>> configs(@RequestParam(required = false) String countryCode) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("countryCode", countryCode);
        result.put("total", (long) adminTariffService.listConfigs(countryCode).size());
        result.put("records", adminTariffService.listConfigs(countryCode));
        return Result.success(result);
    }

    @Operation(summary = "创建税率配置")
    @PostMapping("/configs/create")
    public Result<Map<String, Object>> createConfig(@RequestBody Map<String, Object> body) {
        adminTariffService.createConfig(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "创建成功");
        return Result.success(result);
    }

    @Operation(summary = "更新税率配置")
    @PutMapping("/configs/update")
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        adminTariffService.updateConfig(body);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", body.get("id"));
        result.put("message", "更新成功");
        return Result.success(result);
    }

    @Operation(summary = "删除税率配置")
    @DeleteMapping("/configs/{id}")
    public Result<Map<String, Object>> deleteConfig(@PathVariable Long id) {
        adminTariffService.deleteConfig(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id);
        result.put("message", "删除成功");
        return Result.success(result);
    }

    @Operation(summary = "试算关税")
    @GetMapping("/calculate")
    public Result<Map<String, Object>> calculate(
        @RequestParam String countryCode,
        @RequestParam BigDecimal amount,
        @RequestParam String category) {
        return Result.success(adminTariffService.calculate(countryCode, amount, category));
    }
}

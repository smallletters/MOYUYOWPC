package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
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
  public Result<OperationResult> createConfig(@RequestBody Map<String, Object> body) {
    adminTariffService.createConfig(body);
    OperationResult result = new OperationResult();
    result.setId(body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null);
    result.setMessage("创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新税率配置")
  @PutMapping("/configs/update")
  public Result<OperationResult> updateConfig(@RequestBody Map<String, Object> body) {
    adminTariffService.updateConfig(body);
    OperationResult result = new OperationResult();
    result.setId(body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null);
    result.setMessage("更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除税率配置")
  @DeleteMapping("/configs/{id}")
  public Result<OperationResult> deleteConfig(@PathVariable Long id) {
    adminTariffService.deleteConfig(id);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("删除成功");
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

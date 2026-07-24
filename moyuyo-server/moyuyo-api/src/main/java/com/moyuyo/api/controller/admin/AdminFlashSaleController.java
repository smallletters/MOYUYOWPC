package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.admin.OperationResult;
import com.moyuyo.service.admin.AdminFlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "管理后台 - 秒杀活动管理")
@RestController
@RequestMapping("/api/admin/flash-sales")
@RequiredArgsConstructor
public class AdminFlashSaleController {

  private final AdminFlashSaleService adminFlashSaleService;

  @Operation(summary = "秒杀活动列表")
  @GetMapping("/list")
  public Result<Map<String, Object>> list() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", (long) adminFlashSaleService.listAll().size());
    result.put("records", adminFlashSaleService.listAll());
    return Result.success(result);
  }

  @Operation(summary = "创建秒杀活动")
  @PostMapping("/create")
  public Result<OperationResult> create(@RequestBody Map<String, Object> body) {
    adminFlashSaleService.create(body);
    OperationResult result = new OperationResult();
    result.setId(body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null);
    result.setMessage("创建成功");
    return Result.success(result);
  }

  @Operation(summary = "更新秒杀活动")
  @PutMapping("/update")
  public Result<OperationResult> update(@RequestBody Map<String, Object> body) {
    adminFlashSaleService.update(body);
    OperationResult result = new OperationResult();
    result.setId(body.get("id") != null ? Long.valueOf(body.get("id").toString()) : null);
    result.setMessage("更新成功");
    return Result.success(result);
  }

  @Operation(summary = "删除秒杀活动")
  @DeleteMapping("/{id}")
  public Result<OperationResult> delete(@PathVariable Long id) {
    adminFlashSaleService.delete(id);
    OperationResult result = new OperationResult();
    result.setId(id);
    result.setMessage("删除成功");
    return Result.success(result);
  }

  @Operation(summary = "修改秒杀活动状态")
  @PutMapping("/{id}/status")
  public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                  @RequestParam(required = false) Boolean active,
                                                  @RequestBody(required = false) Map<String, Object> body) {
    // 支持从 RequestParam 或 RequestBody 中获取 active 值
    Boolean activeValue = active;
    if (activeValue == null && body != null && body.containsKey("active")) {
      Object activeObj = body.get("active");
      if (activeObj instanceof Boolean) {
        activeValue = (Boolean) activeObj;
      } else if (activeObj instanceof String) {
        activeValue = Boolean.parseBoolean((String) activeObj);
      }
    }
    adminFlashSaleService.updateStatus(id, activeValue != null ? activeValue.toString() : "false");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("active", activeValue);
    result.put("message", "状态更新成功");
    return Result.success(result);
  }
}

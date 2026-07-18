package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 批量导入控制器
 * 注：DataExportRequestMapper 因多模块类路径问题暂时无法注入，等 mvn install 成功后可恢复注入
 */
@Tag(name = "管理后台 - 批量导入")
@RestController
@RequestMapping("/api/admin/batch-import")
public class AdminBatchImportController {

  @Operation(summary = "导入记录列表")
  @GetMapping("/records")
  public Result<List<Map<String, Object>>> records(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    // TODO: 等 Maven 多模块依赖问题解决后，注入 DataExportRequestMapper 替换为真实查询
    return Result.success(new ArrayList<>());
  }
}

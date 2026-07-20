package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminBatchImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 批量导入")
@RestController
@RequestMapping("/api/admin/batch-import")
@RequiredArgsConstructor
public class AdminBatchImportController {

    private final AdminBatchImportService batchImportService;

    @Operation(summary = "导入记录列表")
    @GetMapping("/records")
    public Result<List<Map<String, Object>>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {
        return Result.success(batchImportService.listRecords(page, size));
    }

    @Operation(summary = "提交导入请求")
    @PostMapping("/import")
    public Result<Map<String, Object>> importData(@RequestBody Map<String, Object> body) {
        return Result.success(batchImportService.importData(body));
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/template/{type}")
    public Result<Map<String, String>> template(@PathVariable String type) {
        return Result.success(batchImportService.getTemplate(type));
    }

    @Operation(summary = "查询导入错误详情")
    @GetMapping("/{id}/errors")
    public Result<List<Map<String, Object>>> errors(@PathVariable Long id) {
        List<Map<String, Object>> errors = batchImportService.getErrors(id);
        return Result.success(errors);
    }

    @Operation(summary = "删除导入记录")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        return Result.success(batchImportService.deleteRecord(id));
    }
}

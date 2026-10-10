package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.dao.admin.entity.KnowledgeBaseEntity;
import com.moyuyo.service.admin.AdminKnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理后台 - 知识库管理")
@RestController
@RequestMapping("/api/admin/knowledge-base")
@RequiredArgsConstructor
public class AdminKnowledgeBaseController {

  private final AdminKnowledgeBaseService adminKnowledgeBaseService;

  @Operation(summary = "知识库列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size) {
    return Result.success(adminKnowledgeBaseService.listAll(category, keyword, page, size));
  }

  @Operation(summary = "文章详情")
  @GetMapping("/{id}")
  public Result<?> detail(@PathVariable Long id) {
    return Result.success(adminKnowledgeBaseService.getById(id));
  }

  @Operation(summary = "创建文章")
  @PostMapping("/create")
  @AdminAudit(action = "CREATE", module = "CONTENT",
      resourceId = "kb-article", detail = "管理员创建知识库文章")
  public Result<Map<String, Object>> create(@RequestBody KnowledgeBaseEntity body) {
    adminKnowledgeBaseService.create(body);
    return Result.success(Map.of("id", body.getId(), "message", "文章创建成功"));
  }

  @Operation(summary = "更新文章")
  @PutMapping("/update")
  @AdminAudit(action = "UPDATE", module = "CONTENT",
      resourceId = "#body.id", detail = "管理员更新知识库文章")
  public Result<Map<String, Object>> update(@RequestBody KnowledgeBaseEntity body) {
    // 校验 id 必填
    if (body == null || body.getId() == null) {
      return Result.error(400, "参数错误：id 不能为空");
    }
    adminKnowledgeBaseService.update(body);
    return Result.success(Map.of("id", body.getId(), "message", "文章更新成功"));
  }

  @Operation(summary = "删除文章")
  @DeleteMapping("/{id}")
  @AdminAudit(action = "DELETE", module = "CONTENT",
      resourceId = "#id", detail = "管理员删除知识库文章")
  public Result<Map<String, Object>> delete(@PathVariable Long id) {
    adminKnowledgeBaseService.delete(id);
    return Result.success(Map.of("id", id, "message", "删除成功"));
  }
}

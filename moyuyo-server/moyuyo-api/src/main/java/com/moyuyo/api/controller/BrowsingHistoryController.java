package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.BrowsingHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "浏览记录")
@RestController
@RequestMapping("/api/v1/browsing-history")
@RequiredArgsConstructor
// 方法级校验：@PathVariable / @RequestParam 上的 @Positive 才能生效
@Validated
public class BrowsingHistoryController {

  private final BrowsingHistoryService browsingHistoryService;

  @Operation(summary = "浏览记录列表（按日期分组）")
  @GetMapping
  public Result<IPage<Map<String, Object>>> list(
      @RequestParam(defaultValue = "1") @Positive(message = "page 必须为正整数") int page,
      @RequestParam(defaultValue = "20") @Positive(message = "size 必须为正整数") int size) {
    return Result.success(browsingHistoryService.listHistoryGrouped(UserContextHolder.getUserId(), page, size));
  }

  @Operation(summary = "清空浏览记录")
  @DeleteMapping
  public Result<Void> clearAll() {
    browsingHistoryService.clearAll(UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "删除单条浏览记录")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable @Positive(message = "浏览记录 ID 必须为正整数") Long id) {
    browsingHistoryService.deleteOne(id, UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "添加浏览记录")
  @PostMapping
  public Result<Void> add(@RequestParam @Positive(message = "商品 ID 必须为正整数") Long productId) {
    browsingHistoryService.addRecord(UserContextHolder.getUserId(), productId);
    return Result.success();
  }
}

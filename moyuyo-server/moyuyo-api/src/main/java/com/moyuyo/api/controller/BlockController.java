package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.BlockEntity;
import com.moyuyo.dao.mapper.BlockMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "黑名单")
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

  private final BlockMapper blockMapper;

  @GetMapping
  public Result<Page<BlockEntity>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.success(blockMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<BlockEntity>()
            .eq(BlockEntity::getUserId, UserContextHolder.getUserId())
            .orderByDesc(BlockEntity::getCreateTime)));
  }

  @PostMapping
  public Result<Void> block(@RequestBody Map<String, Object> body) {
    Long userId = UserContextHolder.getUserId();
    Long targetId = body == null ? null : ((Number) body.get("targetId")).longValue();
    if (targetId == null || targetId.equals(userId)) throw new IllegalArgumentException("目标用户无效");
    BlockEntity exist = blockMapper.selectOne(
        new LambdaQueryWrapper<BlockEntity>()
            .eq(BlockEntity::getUserId, userId)
            .eq(BlockEntity::getTargetId, targetId));
    if (exist != null) return Result.success();
    BlockEntity b = new BlockEntity();
    b.setUserId(userId);
    b.setTargetId(targetId);
    b.setReason((String) body.get("reason"));
    blockMapper.insert(b);
    return Result.success();
  }

  @DeleteMapping("/{targetId}")
  public Result<Void> unblock(@PathVariable Long targetId) {
    blockMapper.delete(new LambdaQueryWrapper<BlockEntity>()
        .eq(BlockEntity::getUserId, UserContextHolder.getUserId())
        .eq(BlockEntity::getTargetId, targetId));
    return Result.success();
  }
}

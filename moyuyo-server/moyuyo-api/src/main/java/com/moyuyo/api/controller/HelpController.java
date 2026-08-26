package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.HelpArticleEntity;
import com.moyuyo.dao.entity.HelpCategoryEntity;
import com.moyuyo.dao.mapper.HelpArticleMapper;
import com.moyuyo.dao.mapper.HelpCategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "帮助中心")
@RestController
@RequestMapping("/api/v1/help")
@RequiredArgsConstructor
public class HelpController {

  private final HelpCategoryMapper categoryMapper;
  private final HelpArticleMapper articleMapper;

  @GetMapping("/categories")
  public Result<List<HelpCategoryEntity>> categories() {
    return Result.success(categoryMapper.selectList(
        new LambdaQueryWrapper<HelpCategoryEntity>()
            .eq(HelpCategoryEntity::getActive, 1)
            .orderByAsc(HelpCategoryEntity::getSortOrder)));
  }

  @GetMapping("/articles")
  public Result<Page<HelpArticleEntity>> articles(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String keyword) {
    LambdaQueryWrapper<HelpArticleEntity> q = new LambdaQueryWrapper<HelpArticleEntity>()
        .eq(HelpArticleEntity::getStatus, 1)
        .orderByAsc(HelpArticleEntity::getSortOrder);
    if (categoryId != null) q.eq(HelpArticleEntity::getCategoryId, categoryId);
    if (keyword != null && !keyword.isBlank()) q.like(HelpArticleEntity::getTitle, keyword);
    return Result.success(articleMapper.selectPage(new Page<>(page, size), q));
  }

  @GetMapping("/articles/{id}")
  public Result<HelpArticleEntity> articleDetail(@PathVariable Long id) {
    HelpArticleEntity a = articleMapper.selectById(id);
    if (a != null) {
      articleMapper.update(null,
          new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<HelpArticleEntity>()
              .eq(HelpArticleEntity::getId, id)
              .setSql("view_count = view_count + 1"));
    }
    return Result.success(a);
  }

  @PostMapping("/articles/{id}/helpful")
  public Result<Void> helpful(@PathVariable Long id) {
    articleMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<HelpArticleEntity>()
            .eq(HelpArticleEntity::getId, id)
            .setSql("helpful_count = helpful_count + 1"));
    return Result.success();
  }
}

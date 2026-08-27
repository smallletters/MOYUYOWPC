package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "商品管理")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;
  private final ProductReviewMapper productReviewMapper;
  private final CategoryMapper categoryMapper;
  private final MissionService missionService;
  private final StringRedisTemplate redisTemplate;

  @Operation(summary = "商品列表（分页+筛选+排序+搜索）")
  @GetMapping
  public Result<Page<ProductEntity>> listProducts(
      @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "二级分类ID") @RequestParam(required = false) Long categoryId,
      @Parameter(description = "一级父分类ID，查全部子分类商品") @RequestParam(required = false) Long parentCategoryId,
      @Parameter(description = "排序字段(price/createdAt/sales)") @RequestParam(required = false) String sortBy,
      @Parameter(description = "排序顺序(asc/desc)") @RequestParam(required = false) String sortOrder,
      @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
      @Parameter(description = "品牌IP ID") @RequestParam(required = false) Long brandIpId) {
    // 若传入 parentCategoryId，先拉子分类 ID 列表，转成 IN 查询
    List<Long> categoryIds = null;
    if (parentCategoryId != null && categoryId == null) {
      List<CategoryEntity> children = categoryMapper.selectList(
          new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CategoryEntity>()
              .eq(CategoryEntity::getParentId, parentCategoryId));
      categoryIds = new ArrayList<>();
      for (CategoryEntity c : children) categoryIds.add(c.getId());
      // 没有子分类时，传一个 -1L 强制空集
      if (categoryIds.isEmpty()) categoryIds.add(-1L);
      categoryId = null;
    }
    Page<ProductEntity> result;
    if (categoryIds != null) {
      result = productService.listProductsByCategoryIds(page, size, categoryIds, sortBy, sortOrder, keyword, brandIpId);
    } else {
      result = productService.listProducts(page, size, categoryId, sortBy, sortOrder, keyword, null, null, brandIpId);
    }
    // 给每个商品附加 rating / reviewCount（聚合 mo_product_review）
    if (result != null && result.getRecords() != null && !result.getRecords().isEmpty()) {
      java.util.List<Long> ids = new ArrayList<>();
      for (ProductEntity p : result.getRecords()) ids.add(p.getId());
      // 仅查询 APPROVED 状态的评论
      List<ProductReviewEntity> reviews = productReviewMapper.selectList(
          new LambdaQueryWrapper<ProductReviewEntity>()
              .select(ProductReviewEntity::getProductId, ProductReviewEntity::getRating)
              .eq(ProductReviewEntity::getStatus, "APPROVED")
              .in(ProductReviewEntity::getProductId, ids));
      Map<Long, List<ProductReviewEntity>> grouped = reviews.stream()
          .collect(Collectors.groupingBy(ProductReviewEntity::getProductId));
      for (ProductEntity p : result.getRecords()) {
        List<ProductReviewEntity> list = grouped.getOrDefault(p.getId(), Collections.emptyList());
        if (list.isEmpty()) {
          p.setRating(0.0);
          p.setReviewCount(0);
        } else {
          double avg = list.stream().mapToInt(ProductReviewEntity::getRating).average().orElse(0);
          p.setRating(Math.round(avg * 10) / 10.0); // 保留 1 位小数
          p.setReviewCount(list.size());
        }
      }
    }
    return Result.success(result);
  }

  @Operation(summary = "商品详情（含 SKU 和图片）")
  @GetMapping("/{id}")
  public Result<ProductEntity> getProductDetail(@PathVariable Long id) {
    ProductEntity product = productService.getProductWithDetails(id);
    // 浏览商品触发"浏览 5 个商品"任务进度（同一商品 5 分钟内不重复计数，避免刷新刷分）
    triggerViewMission(id);
    return Result.success(product);
  }

  /** 浏览商品任务进度触发：用 Redis 短期去重（同 userId+productId 5 分钟内只算 1 次）。 */
  private void triggerViewMission(Long productId) {
    try {
      Long userId = UserContextHolder.getUserId();
      if (userId == null) return;
      String dedupKey = "mission:view:dedup:" + userId + ":" + productId;
      Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(
          dedupKey, "1", Duration.ofMinutes(5));
      if (Boolean.FALSE.equals(firstTime)) {
        return; // 5 分钟内重复浏览，不计
      }
      // 查找"浏览"任务（按 name 包含"浏览"匹配，兼容后续多类浏览任务）
      missionService.listAllMissions().stream()
          .filter(m -> "DAILY".equalsIgnoreCase(m.getType())
              && m.getActive() != null && m.getActive() == 1
              && m.getName() != null && m.getName().contains("浏览"))
          .findFirst()
          .ifPresent(m -> missionService.incrementProgress(userId, m.getId(), 1));
    } catch (Exception e) {
      // 任务进度失败不应影响商品详情主流程
      log.warn("[product] trigger view mission failed: productId={}", productId, e);
    }
  }
}

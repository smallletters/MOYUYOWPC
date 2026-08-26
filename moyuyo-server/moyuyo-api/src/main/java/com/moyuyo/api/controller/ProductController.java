package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "商品管理")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;
  private final ProductReviewMapper productReviewMapper;
  private final CategoryMapper categoryMapper;

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
    return Result.success(productService.getProductWithDetails(id));
  }
}

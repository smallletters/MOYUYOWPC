package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "商品管理")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;

  @Operation(summary = "商品列表（分页+筛选+排序+搜索）")
  @GetMapping
  public Result<Page<ProductEntity>> listProducts(
      @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
      @Parameter(description = "排序字段(price/createdAt/sales)") @RequestParam(required = false) String sortBy,
      @Parameter(description = "排序顺序(asc/desc)") @RequestParam(required = false) String sortOrder,
      @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
      @Parameter(description = "品牌IP ID") @RequestParam(required = false) Long brandIpId) {
    return Result.success(productService.listProducts(page, size, categoryId, sortBy, sortOrder, keyword, null, null, brandIpId));
  }

  @Operation(summary = "商品详情（含 SKU 和图片）")
  @GetMapping("/{id}")
  public Result<ProductEntity> getProductDetail(@PathVariable Long id) {
    return Result.success(productService.getProductWithDetails(id));
  }
}

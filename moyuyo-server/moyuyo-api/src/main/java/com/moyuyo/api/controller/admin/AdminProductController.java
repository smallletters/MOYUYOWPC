package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "管理后台 - 商品管理")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

  private final ProductService productService;
  private final ProductMapper productMapper;

  @Operation(summary = "商品列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortOrder) {
    try {
      return Result.success(productService.listProducts(page, size, categoryId, sortBy, sortOrder, keyword, null));
    } catch (Exception e) {
      return Result.error("查询商品列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "商品详情")
  @GetMapping("/{id}")
  public Result<?> detail(@PathVariable Long id) {
    try {
      return Result.success(productService.getProductDetail(id));
    } catch (Exception e) {
      return Result.error("查询商品详情失败: " + e.getMessage());
    }
  }

  @Operation(summary = "创建商品")
  @PostMapping("/create")
  public Result<Map<String, Object>> createProduct(@RequestBody Map<String, Object> body) {
    try {
      ProductEntity entity = new ProductEntity();
      entity.setName((String) body.get("name"));
      if (body.get("price") != null) {
        entity.setPrice(new BigDecimal(body.get("price").toString()));
      }
      if (body.get("originalPrice") != null) {
        entity.setOriginalPrice(new BigDecimal(body.get("originalPrice").toString()));
      }
      if (body.get("categoryId") != null) {
        entity.setCategoryId(Long.valueOf(body.get("categoryId").toString()));
      }
      entity.setMainImage((String) body.get("mainImage"));
      entity.setDetail((String) body.get("detail"));
      entity.setStock(body.get("stock") != null ? Integer.valueOf(body.get("stock").toString()) : 0);
      entity.setOnSale((Boolean) body.getOrDefault("onSale", true));
      if (body.get("spuCode") != null) {
        entity.setSpuCode((String) body.get("spuCode"));
      }

      productMapper.insert(entity);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", entity.getId());
      result.put("message", "商品创建成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("创建商品失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新商品")
  @PutMapping("/{id}")
  public Result<Map<String, Object>> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      ProductEntity entity = productMapper.selectById(id);
      if (entity == null) {
        return Result.error("商品不存在");
      }

      if (body.get("name") != null) entity.setName((String) body.get("name"));
      if (body.get("price") != null) entity.setPrice(new BigDecimal(body.get("price").toString()));
      if (body.get("originalPrice") != null) entity.setOriginalPrice(new BigDecimal(body.get("originalPrice").toString()));
      if (body.get("categoryId") != null) entity.setCategoryId(Long.valueOf(body.get("categoryId").toString()));
      if (body.get("mainImage") != null) entity.setMainImage((String) body.get("mainImage"));
      if (body.get("detail") != null) entity.setDetail((String) body.get("detail"));
      if (body.get("stock") != null) entity.setStock(Integer.valueOf(body.get("stock").toString()));
      if (body.containsKey("onSale")) entity.setOnSale((Boolean) body.get("onSale"));
      if (body.get("spuCode") != null) entity.setSpuCode((String) body.get("spuCode"));

      productMapper.updateById(entity);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "商品更新成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("更新商品失败: " + e.getMessage());
    }
  }
}

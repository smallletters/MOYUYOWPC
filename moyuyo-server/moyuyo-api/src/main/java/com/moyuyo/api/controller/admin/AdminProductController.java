package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理后台 - 商品管理")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

  private final ProductService productService;

  @Operation(summary = "商品列表")
  @GetMapping("/list")
  public Result<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String stockStatus,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortOrder) {
    // 合并 categoryId 和 category 参数
    String catParam = categoryId != null ? categoryId : category;
    Long catIdLong = null;
    if (catParam != null && !catParam.isEmpty()) {
      try {
        catIdLong = Long.valueOf(catParam);
      } catch (NumberFormatException e) {
        // 字符串分类名转换为ID
        catIdLong = resolveCategoryId(catParam);
      }
    }
    return Result.success(productService.listProducts(page, size, catIdLong, sortBy, sortOrder, keyword, status, stockStatus, null));
  }

  @Operation(summary = "商品详情")
  @GetMapping("/{id}")
  public Result<?> detail(@PathVariable Long id) {
    try {
      ProductEntity entity = productService.getProductWithDetails(id);
      return Result.success(entity);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("查询商品详情失败: " + e.getMessage());
    }
  }

  @Operation(summary = "创建商品")
  @PostMapping("/create")
  public Result<Map<String, Object>> createProduct(@RequestBody Map<String, Object> body) {
    try {
      ProductEntity entity = productService.createProduct(body);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", entity.getId());
      result.put("message", "商品创建成功");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("创建商品失败: " + e.getMessage());
    }
  }

  @Operation(summary = "更新商品")
  @PutMapping("/{id}")
  public Result<Map<String, Object>> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      productService.updateProduct(id, body);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "商品更新成功");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("更新商品失败: " + e.getMessage());
    }
  }

  @Operation(summary = "切换商品上架/下架状态")
  @PutMapping("/{id}/status")
  public Result<Map<String, Object>> toggleStatus(@PathVariable Long id) {
    try {
      ProductEntity entity = productService.toggleProductStatus(id);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("onSale", entity.getOnSale());
      result.put("message", entity.getOnSale() ? "商品已上架" : "商品已下架");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      return Result.error("切换商品状态失败: " + e.getMessage());
    }
  }

  @Operation(summary = "批量操作商品")
  @PostMapping("/batch")
  public Result<Map<String, Object>> batchAction(@RequestBody Map<String, Object> body) {
    String action = (String) body.get("action");
    List<Long> ids = extractIds(body.get("ids"));
    if (ids.isEmpty()) {
      return Result.error("请选择要操作的商品");
    }
    try {
      int count = productService.batchProductAction(action, ids);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("count", count);
      result.put("message", "批量操作成功，共处理 " + count + " 条记录");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("批量操作失败: " + e.getMessage());
    }
  }

  /** 安全地从请求体中提取ID列表，兼容 Integer/Long 类型 */
  private List<Long> extractIds(Object idsObj) {
    List<Long> ids = new ArrayList<>();
    if (idsObj instanceof List) {
      for (Object item : (List<?>) idsObj) {
        if (item instanceof Number) {
          ids.add(((Number) item).longValue());
        }
      }
    }
    return ids;
  }

  /** 将前端分类名转换为分类ID */
  private Long resolveCategoryId(String categoryName) {
    if (categoryName == null || categoryName.isEmpty()) return null;
    return switch (categoryName) {
      case "clothing" -> 1L;
      case "electronics" -> 2L;
      case "accessories" -> 3L;
      case "home" -> 4L;
      case "health" -> 1L;
      case "food" -> 2L;
      case "beauty" -> 3L;
      case "daily" -> 4L;
      default -> {
        try { yield Long.valueOf(categoryName); }
        catch (NumberFormatException e) { yield null; }
      }
    };
  }
}

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
  private final com.moyuyo.dao.mapper.CategoryMapper categoryMapper;
  private final com.moyuyo.dao.mapper.ProductMapper productMapper;
  // 注入 WooCommerce 同步服务：手动触发商品的双向同步
  private final com.moyuyo.service.WooCommerceSyncService wooCommerceSyncService;
  // 注入 WooCommerce 客户端：用于配置检查
  private final com.moyuyo.service.impl.WooCommerceClient wooCommerceClient;

  /**
   * 幂等守卫：标记 WooCommerce 商品拉取任务是否正在进行。
   * <p>
   * P1 修复背景：原实现每次请求都 new Thread().start()，并发触发会创建大量原生线程，触发器可能瞬间拉起 100+ 个同步任务，
   * 既重复消费 WooCommerce API 配额，又会拖垮 MySQL 连接池。本守卫保证同一时刻只有一个拉取任务在跑。
   */
  private static final java.util.concurrent.atomic.AtomicBoolean WOO_PRODUCT_PULL_RUNNING = new java.util.concurrent.atomic.AtomicBoolean(false);

  /**
   * WooCommerce 拉取任务专用线程池：固定大小 1，仅承载本任务，避免每次 new Thread() 的资源浪费。
   * 使用守护线程（daemon=true），JVM 退出时自动清理。
   */
  private static final java.util.concurrent.ExecutorService WOO_PRODUCT_PULL_EXECUTOR =
      java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "woo-product-pull");
        t.setDaemon(true);
        return t;
      });

  @Operation(summary = "商品分类列表")
  @GetMapping("/categories")
  public Result<?> categories() {
    try {
      // 返回所有分类
      java.util.List<com.moyuyo.dao.entity.CategoryEntity> list =
          categoryMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
      return Result.success(list);
    } catch (Exception e) {
      return Result.error("查询分类列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "品牌列表")
  @GetMapping("/brands")
  public Result<?> brands() {
    try {
      // 当前数据模型没有独立的品牌表，从商品表去重获取 brandId
      java.util.List<java.util.Map<String, Object>> rows = productMapper.selectMaps(
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.moyuyo.dao.entity.ProductEntity>()
              .select("DISTINCT brand_id AS brandId")
              .isNotNull("brand_id")
              .ne("brand_id", 0));
      return Result.success(rows);
    } catch (Exception e) {
      return Result.error("查询品牌列表失败: " + e.getMessage());
    }
  }

  @Operation(summary = "关联商品轻量搜索（用于 Upsell / Cross-sell 选择弹窗）")
  @GetMapping("/lite")
  public Result<?> searchLite(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) Long excludeId) {
    try {
      // 限制 size 上限，避免单次返回过多
      int limit = Math.max(1, Math.min(size, 100));
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.moyuyo.dao.entity.ProductEntity> qw =
          new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.moyuyo.dao.entity.ProductEntity>()
              .select("id", "name", "spu_code AS spuCode", "price", "main_image AS mainImage", "on_sale AS onSale")
              // 仅返回正常商品（下架的也可以选，便于上架后启用）
              .eq("deleted", 0)
              .orderByDesc("on_sale")
              .orderByDesc("update_time")
              .last("LIMIT " + limit);
      if (keyword != null && !keyword.isBlank()) {
        // 关键字命中：商品名称 OR SPU 编码（按 WC 搜索行为对齐）
        qw.and(w -> w.like("name", keyword).or().like("spu_code", keyword));
      }
      if (excludeId != null) {
        qw.ne("id", excludeId);
      }
      java.util.List<java.util.Map<String, Object>> rows = productMapper.selectMaps(qw);
      return Result.success(rows);
    } catch (Exception e) {
      return Result.error("搜索关联商品失败: " + e.getMessage());
    }
  }

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
    // 列表接口补齐 images[]（每商品的商品图库），便于前端列表展示封面图
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.moyuyo.dao.entity.ProductEntity> productPage =
        productService.listProducts(page, size, catIdLong, sortBy, sortOrder, keyword, status, stockStatus, null);
    if (productPage != null && productPage.getRecords() != null && !productPage.getRecords().isEmpty()) {
      java.util.List<Long> ids = new java.util.ArrayList<>();
      for (com.moyuyo.dao.entity.ProductEntity p : productPage.getRecords()) ids.add(p.getId());
      java.util.Map<Long, java.util.List<com.moyuyo.dao.entity.ProductImageEntity>> imgsMap =
          productService.getImagesByProductIds(ids);
      // 同时批量取 SKU 子表：用于列表 SKU 列展示，避免前端表格空白
      // SKU 子表是 mo_product_sku（与 SPU 表 1:N），单 SKU 商品展示主 SKU_code，多 SKU 商品展示"主SKU +N"
      java.util.Map<Long, java.util.List<com.moyuyo.dao.entity.ProductSkuEntity>> skusMap =
          productService.getSkusByProductIds(ids);
      for (com.moyuyo.dao.entity.ProductEntity p : productPage.getRecords()) {
        p.setImages(imgsMap.getOrDefault(p.getId(), java.util.Collections.emptyList()));
        java.util.List<com.moyuyo.dao.entity.ProductSkuEntity> skus =
            skusMap.getOrDefault(p.getId(), java.util.Collections.emptyList());
        p.setSkus(skus);
      }
    }
    return Result.success(productPage);
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
      // 前置参数校验：name 为 NOT NULL 字段，空 body 时直接返回友好错误而非数据库异常
      Object nameObj = body.get("name");
      if (nameObj == null || nameObj.toString().trim().isEmpty()) {
        return Result.error(400, "商品名称不能为空");
      }
      // categoryId 也是 NOT NULL 字段，前置校验避免 SQL 异常
      Object categoryIdObj = body.get("categoryId");
      if (categoryIdObj == null || categoryIdObj.toString().trim().isEmpty()) {
        return Result.error(400, "商品分类ID不能为空");
      }
      ProductEntity entity = productService.createProduct(body);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", entity.getId());
      result.put("message", "商品创建成功");
      return Result.success(result);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
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

  @Operation(summary = "删除商品")
  @DeleteMapping("/{id}")
  public Result<Map<String, Object>> deleteProduct(@PathVariable Long id) {
    try {
      int count = productService.batchProductAction("delete", java.util.Collections.singletonList(id));
      if (count == 0) {
        return Result.error(404, "商品不存在或已删除");
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("message", "商品删除成功");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("删除商品失败: " + e.getMessage());
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

  /**
   * 检查 WooCommerce 是否已配置，如未配置则返回错误结果
   */
  private Result<?> checkWooCommerceConfigured() {
    try {
      if (!wooCommerceClient.isConfigured()) {
        return Result.error(503, "WooCommerce 未配置或使用默认占位符地址，请先配置 WOOCOMMERCE_URL / CONSUMER_KEY / CONSUMER_SECRET 环境变量");
      }
      return null; // null 表示通过检查
    } catch (Exception e) {
      return Result.error(503, "WooCommerce 配置检查失败: " + e.getMessage());
    }
  }

  /**
   * 从 WooCommerce 拉取全量商品到本地。
   * 异步执行，立即返回给前端。
   * <p>
   * P1 资源修复：原实现使用 new Thread() 创建原生线程，无线程池管控 / 异常传递 / 任务追踪 / 取消能力，
   * 高并发触发时会无限堆积。改用：
   * 1) 静态单线程 ExecutorService 复用线程，避免每次新建
   * 2) AtomicBoolean 幂等守卫，并发点击只接受第一个，其余返回"任务进行中"
   * 3) finally 块释放守卫，避免线程崩溃后守卫永远卡住
   */
  @Operation(summary = "从 WooCommerce 拉取商品")
  @PostMapping("/sync-from-woo")
  public Result<Map<String, Object>> syncFromWoo() {
    Result<?> check = checkWooCommerceConfigured();
    if (check != null) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("message", check.getMessage());
      return Result.error(503, (String) err.get("message"));
    }
    // 幂等守卫：若已有任务在跑，直接返回
    if (!WOO_PRODUCT_PULL_RUNNING.compareAndSet(false, true)) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("message", "已有 WooCommerce 商品拉取任务在进行中，请稍后再试");
      result.put("running", true);
      return Result.success(result);
    }
    try {
      WOO_PRODUCT_PULL_EXECUTOR.submit(() -> {
        try {
          wooCommerceSyncService.syncProductsFromWooCommerce();
        } catch (Exception ex) {
          org.slf4j.LoggerFactory.getLogger(AdminProductController.class)
              .error("从 WooCommerce 拉取商品失败: {}", ex.getMessage(), ex);
        } finally {
          // 无论成功失败都要释放守卫，否则后续请求全部被拒
          WOO_PRODUCT_PULL_RUNNING.set(false);
        }
      });
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("message", "WooCommerce 商品拉取任务已启动");
      result.put("running", true);
      return Result.success(result);
    } catch (Exception e) {
      // 提交任务本身失败也要释放守卫
      WOO_PRODUCT_PULL_RUNNING.set(false);
      return Result.error("启动 WooCommerce 拉取任务失败: " + e.getMessage());
    }
  }

  /**
   * 将单个商品推送到 WooCommerce（首次同步或修正已同步的记录）。
   */
  @Operation(summary = "推送商品到 WooCommerce")
  @PostMapping("/{id}/push-to-woo")
  public Result<Map<String, Object>> pushToWoo(@PathVariable Long id) {
    Result<?> check = checkWooCommerceConfigured();
    if (check != null) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("message", check.getMessage());
      return Result.error(503, (String) err.get("message"));
    }
    try {
      ProductEntity entity = productMapper.selectById(id);
      if (entity == null) {
        return Result.error(404, "商品不存在: " + id);
      }
      Long wooProductId = wooCommerceSyncService.pushProductToWooCommerce(entity);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("wooProductId", wooProductId);
      result.put("message", wooProductId != null
          ? "商品已推送到 WooCommerce，wooProductId=" + wooProductId
          : "商品推送失败，请检查 WooCommerce 配置或网络");
      return wooProductId != null ? Result.success(result) : Result.error(500, (String) result.get("message"));
    } catch (Exception e) {
      return Result.error("推送商品到 WooCommerce 失败: " + e.getMessage());
    }
  }

  /**
   * 批量推送本地未同步到 WooCommerce 的商品。
   * 异步执行避免长时间阻塞 HTTP 响应。
   */
  @Operation(summary = "批量推送商品到 WooCommerce")
  @PostMapping("/push-all-to-woo")
  public Result<Map<String, Object>> pushAllToWoo() {
    Result<?> check = checkWooCommerceConfigured();
    if (check != null) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("message", check.getMessage());
      return Result.error(503, (String) err.get("message"));
    }
    try {
      // 异步执行批量推送，避免 HTTP 请求超时
      Map<String, Integer> stat = wooCommerceSyncService.pushAllProductsToWooCommerce();
      Map<String, Object> result = new LinkedHashMap<>(stat);
      result.put("message", "批量推送完成：成功 " + stat.getOrDefault("success", 0)
          + "，失败 " + stat.getOrDefault("failed", 0)
          + "，跳过 " + stat.getOrDefault("skipped", 0));
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("批量推送 WooCommerce 失败: " + e.getMessage());
    }
  }

  /**
   * 从 WooCommerce 拉取单个商品的完整数据覆盖本地（按本地商品 ID，需已关联 wooProductId）。
   */
  @Operation(summary = "从 WooCommerce 拉取单个商品更新")
  @PostMapping("/{id}/pull-from-woo")
  public Result<Map<String, Object>> pullFromWoo(@PathVariable Long id) {
    Result<?> check = checkWooCommerceConfigured();
    if (check != null) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("message", check.getMessage());
      return Result.error(503, (String) err.get("message"));
    }
    try {
      com.moyuyo.dao.entity.ProductEntity updated = wooCommerceSyncService.pullProductFromWooCommerce(id);
      if (updated == null) {
        // 先检查本地商品是否存在
        com.moyuyo.dao.entity.ProductEntity local = productMapper.selectById(id);
        if (local == null) {
          return Result.error(404, "商品不存在: " + id);
        }
        if (local.getWooProductId() == null) {
          return Result.error(400, "该商品尚未关联 WooCommerce 商品，请先推送到 WC 或手动录入 WC 商品 ID 后再拉取");
        }
        return Result.error(502, "WooCommerce 拉取失败，请检查 WC 服务可用性");
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("wooProductId", updated.getWooProductId());
      result.put("name", updated.getName());
      result.put("regularPrice", updated.getOriginalPrice());
      result.put("salePrice", updated.getPrice());
      result.put("stockQuantity", updated.getStock());
      result.put("stockStatus", updated.getStockStatus());
      result.put("sku", updated.getSpuCode());
      result.put("type", updated.getProductType());
      result.put("weight", updated.getWeight());
      result.put("tags", updated.getTags());
      result.put("shortDescription", updated.getShortDetail());
      result.put("description", updated.getDetail());
      result.put("mainImage", updated.getMainImage());
      result.put("onSale", updated.getOnSale());
      result.put("dimensions", parseAttributesToMap(updated.getAttributes()) != null
          ? parseAttributesToMap(updated.getAttributes()).get("dimensions") : null);
      result.put("wooModified", updated.getWooModified());
      result.put("message", "已从 WooCommerce 拉取最新数据");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("从 WooCommerce 拉取商品失败: " + e.getMessage());
    }
  }

  /**
   * 从 WooCommerce 批量同步所有关联商品的库存。
   */
  @Operation(summary = "从 WooCommerce 批量同步库存")
  @PostMapping("/sync-stock-from-woo")
  public Result<Map<String, Object>> syncStockFromWoo() {
    Result<?> check = checkWooCommerceConfigured();
    if (check != null) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("message", check.getMessage());
      return Result.error(503, (String) err.get("message"));
    }
    try {
      Map<String, Integer> stat = wooCommerceSyncService.syncAllStocksFromWooCommerce();
      Map<String, Object> result = new LinkedHashMap<>(stat);
      result.put("message", "库存同步完成：共 " + stat.get("total") + " 个关联商品，更新 " + stat.get("updated") + " 个");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("库存同步失败: " + e.getMessage());
    }
  }

  /**
   * 从 WooCommerce 同步单个商品的库存（仅更新库存相关字段）。
   */
  @Operation(summary = "从 WooCommerce 同步单个商品库存")
  @PostMapping("/{id}/sync-stock")
  public Result<Map<String, Object>> syncSingleStock(@PathVariable Long id) {
    Result<?> check = checkWooCommerceConfigured();
    if (check != null) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("message", check.getMessage());
      return Result.error(503, (String) err.get("message"));
    }
    try {
      ProductEntity updated = wooCommerceSyncService.syncStockFromWooCommerce(id);
      if (updated == null) {
        ProductEntity local = productMapper.selectById(id);
        if (local == null) return Result.error(404, "商品不存在: " + id);
        if (local.getWooProductId() == null) return Result.error(400, "该商品尚未关联 WooCommerce");
        return Result.error(502, "WooCommerce 库存拉取失败");
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", id);
      result.put("stock", updated.getStock());
      result.put("stockStatus", updated.getStockStatus());
      result.put("message", "库存已同步");
      return Result.success(result);
    } catch (Exception e) {
      return Result.error("库存同步失败: " + e.getMessage());
    }
  }

  /**
   * 解析 MySQL 的 JSON 类型 attributes 字段为 Map
   */
  private Map<String, Object> parseAttributesToMap(String attributesJson) {
    if (attributesJson == null || attributesJson.isEmpty()) return null;
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.readValue(attributesJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return null;
    }
  }

  /** 安全地从请求体中提取ID列表，兼容 Integer/Long/String 类型（雪花ID序列化为字符串后需兼容） */
  private List<Long> extractIds(Object idsObj) {
    List<Long> ids = new ArrayList<>();
    if (idsObj instanceof List) {
      for (Object item : (List<?>) idsObj) {
        if (item instanceof Number) {
          ids.add(((Number) item).longValue());
        } else if (item instanceof String) {
          try {
            ids.add(Long.valueOf((String) item));
          } catch (NumberFormatException ignored) {
            // 忽略无法解析的字符串项
          }
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

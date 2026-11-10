package com.moyuyo.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.service.MemberPriceService;
import com.moyuyo.service.MissionService;
import com.moyuyo.service.ProductService;
import com.moyuyo.service.admin.SystemConfigService;
import com.moyuyo.service.support.ProductAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
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
// 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
@SuppressWarnings("null")
public class ProductController {

  private final ProductService productService;
  private final ProductReviewMapper productReviewMapper;
  private final CategoryMapper categoryMapper;
  private final MissionService missionService;
  private final MemberPriceService memberPriceService;
  private final FlashSaleMapper flashSaleMapper;
  private final StringRedisTemplate redisTemplate;
  private final com.moyuyo.service.impl.CampaignPricingServiceImpl campaignPricingService;
  // 系统配置：读取商品税率供 APP 结算页预估税费
  private final SystemConfigService systemConfigService;

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
      // 一级分类自身也可能直接挂商品（例如一级分类下暂无二级分类），需一并返回
      categoryIds.add(parentCategoryId);
      for (CategoryEntity c : children) categoryIds.add(c.getId());
      categoryId = null;
    }
    Page<ProductEntity> result;
    if (categoryIds != null) {
      // APP 端商品列表只展示在售商品
      result = productService.listProductsByCategoryIds(page, size, categoryIds, sortBy, sortOrder, keyword, "active", brandIpId);
    } else {
      // APP 端商品列表只展示在售商品（status=active 强制过滤已下架）
      result = productService.listProducts(page, size, categoryId, sortBy, sortOrder, keyword, "active", null, brandIpId);
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
    // 销售期生效：列表价格与详情页保持同一口径
    if (result != null && result.getRecords() != null) {
      result.getRecords().forEach(this::applySaleWindow);
    }
    // 注入营销活动价（列表批量一次查询，避免 N+1）
    if (result != null && result.getRecords() != null) {
      campaignPricingService.fillProductCampaigns(result.getRecords(), UserContextHolder.getUserId());
    }
    return Result.success(result);
  }

  @Operation(summary = "商品税率（APP 结算页预估税费）")
  @GetMapping("/config/tax-rate")
  public Result<Map<String, Object>> getProductTaxRate() {
    BigDecimal rate = BigDecimal.ZERO;
    try {
      Object value = systemConfigService.getConfig("order").get("product_tax_rate");
      if (value != null && !String.valueOf(value).isBlank()) {
        rate = new BigDecimal(String.valueOf(value).trim());
      }
    } catch (Exception e) {
      // 配置读取失败按 0 处理，不阻断结算页
      log.warn("[product] 读取商品税率失败: {}", e.getMessage());
    }
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("rate", rate);
    return Result.success(result);
  }

  @Operation(summary = "商品详情（含 SKU 和图片）")
  @GetMapping("/{id}")
  public Result<ProductEntity> getProductDetail(@PathVariable Long id) {
    return buildDetailResult(productService.getProductWithDetails(id));
  }

  @Operation(summary = "按短链(slug)查询商品详情")
  @GetMapping("/slug/{slug}")
  public Result<ProductEntity> getProductDetailBySlug(@PathVariable String slug) {
    return buildDetailResult(productService.getProductWithDetailsBySlug(slug));
  }

  /**
   * 详情统一组装：销售期校验 → 营销活动 → 会员价 → 浏览任务。
   * ID 与 slug 两个入口共用，避免逻辑分叉。
   */
  private Result<ProductEntity> buildDetailResult(ProductEntity product) {
    // 销售起止日期生效：不在促销期时恢复原价展示
    applySaleWindow(product);
    Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
    // 评论开关：APP 据此隐藏评价 Tab / 入口
    product.setReviewsAllowed(ProductAttributes.bool(attrs, "reviews_allowed", true));
    // 缺货订购：APP 据此允许库存为 0 时继续加购/下单
    product.setAllowBackorder(!"no".equalsIgnoreCase(ProductAttributes.str(attrs, "backorders", "no")));
    // 虚拟商品：APP 结算时不再强制要求收货地址
    product.setIsVirtual(ProductAttributes.bool(attrs, "is_virtual", false));
    // 税费信息：APP 结算页按税率与税状态/税类预估税费
    product.setTaxStatus(ProductAttributes.str(attrs, "tax_status", "taxable"));
    product.setTaxClass(ProductAttributes.str(attrs, "tax_class", "standard"));
    // 营销活动价优先于会员价；活动商品不再展示会员价
    campaignPricingService.fillProductCampaign(product, UserContextHolder.getUserId());
    // 会员价：按当前登录用户的会员等级动态计算展示价（未登录/无折扣时为 null）
    fillMemberPrice(product);
    // 浏览商品触发"浏览 5 个商品"任务进度（同一商品 5 分钟内不重复计数，避免刷新刷分）
    triggerViewMission(product.getId());
    return Result.success(product);
  }

  /**
   * 销售期生效：配置了 sale_price_dates_from / to 且当前不在区间内时，
   * 商品价与所有 SKU 价统一回退到常规价，避免过期促销价继续在 APP 展示。
   */
  private void applySaleWindow(ProductEntity product) {
    if (product == null) {
      return;
    }
    Map<String, Object> attrs = ProductAttributes.parse(product.getAttributes());
    if (ProductAttributes.isSaleWindowActive(attrs)) {
      return;
    }
    BigDecimal regularPrice = product.getOriginalPrice();
    product.setPrice(ProductAttributes.effectivePrice(
        attrs, null, null, product.getPrice(), regularPrice));
    // 详情接口会带 SKU：逐条回退，保证 APP 选规格时拿到的是生效价
    if (product.getSkus() != null) {
      for (ProductSkuEntity sku : product.getSkus()) {
        sku.setPrice(ProductAttributes.effectivePrice(
            attrs, sku.getSkuCode(), sku.getSpec(), sku.getPrice(), regularPrice));
      }
    }
  }

  /** 按当前用户会员等级计算会员展示价；折扣与下单时使用的配置一致 */
  private void fillMemberPrice(ProductEntity product) {
    if (product == null) {
      return;
    }
    // 秒杀标记与登录态无关，需始终返回，供前端隐藏 attributes 里的历史会员价兜底
    boolean flashSaleActive = isFlashSaleActive(product.getId());
    product.setFlashSaleActive(flashSaleActive);
    Long userId = UserContextHolder.getUserId();
    if (userId == null || product.getPrice() == null) {
      return;
    }
    // 秒杀/营销活动商品不叠加会员折扣（与下单口径一致），详情页也不展示会员价
    if (flashSaleActive || product.getCampaignId() != null) {
      return;
    }
    BigDecimal rate = memberPriceService.getDiscountForUser(userId);
    if (rate == null || rate.compareTo(BigDecimal.ONE) >= 0 || rate.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    product.setMemberPriceRate(rate);
    product.setMemberPrice(product.getPrice().multiply(rate)
        .setScale(2, RoundingMode.HALF_UP));
  }

  /** 商品是否处于进行中的秒杀活动 */
  private boolean isFlashSaleActive(Long productId) {
    if (productId == null) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    Long count = flashSaleMapper.selectCount(new LambdaQueryWrapper<FlashSaleEntity>()
        .eq(FlashSaleEntity::getProductId, productId)
        .eq(FlashSaleEntity::getActive, true)
        .le(FlashSaleEntity::getStartTime, now)
        .ge(FlashSaleEntity::getEndTime, now));
    return count != null && count > 0;
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

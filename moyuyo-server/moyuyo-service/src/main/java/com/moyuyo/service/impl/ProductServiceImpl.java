package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductImageEntity;
import com.moyuyo.dao.entity.ProductPresaleEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductImageMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductPresaleMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.ProductService;
import com.moyuyo.service.WooCommerceSyncService;
import com.moyuyo.service.support.ProductAttributes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

  /** 预售类型：展示型（不改变下单价格，仅展示预售时间/发货时间） */
  private static final String PRESALE_TYPE_DISPLAY = "DISPLAY";
  /** 预售类型：全款预售 */
  private static final String PRESALE_TYPE_FULL = "FULL";
  /** 预售类型：定金 + 尾款 */
  private static final String PRESALE_TYPE_DEPOSIT = "DEPOSIT";

  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;
  private final ProductImageMapper productImageMapper;
  // 商品预售配置 Mapper（mo_product_presale，1:1 商品）
  private final ProductPresaleMapper productPresaleMapper;
  // 订单 Mapper：删除预售配置前校验是否仍有未完结的定金单
  private final OrderMapper orderMapper;
  // 注入 WooCommerce 同步服务：商品变更后自动推送到 WooCommerce
  private final WooCommerceSyncService wooCommerceSyncService;
  // Jackson 解析器：解析 attributes JSON 中的变体数据，用于 SKU 落库
  private final ObjectMapper objectMapper;

  @Override
  public Page<ProductEntity> listProducts(int page, int size, Long categoryId, String sortBy, String sortOrder, String keyword, String status, String stockStatus, Long brandIpId) {
    LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
        .eq(categoryId != null, ProductEntity::getCategoryId, categoryId)
        .eq(brandIpId != null, ProductEntity::getBrandIpId, brandIpId)
        // 关键词搜索：商品名称 OR SPU编码 OR 关联 SKU 的 sku_code（子查询），对齐前端"商品名称 / SKU"搜索框提示
        .and(StringUtils.isNotBlank(keyword), w -> w
            .like(ProductEntity::getName, keyword)
            .or().like(ProductEntity::getSpuCode, keyword)
            .or().exists("(SELECT 1 FROM mo_product_sku s WHERE s.product_id = mo_product.id AND s.sku_code LIKE CONCAT('%', {0}, '%'))", keyword)
            // GTIN/UPC/EAN/ISBN 也参与关键词搜索（存储于 attributes JSON）
            .or().apply("JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.global_unique_id')) LIKE CONCAT('%', {0}, '%')", keyword));

    // 状态筛选：active=在售；inactive/draft/pending 基于真实审核记录派生
    applyStatusFilter(wrapper, status);

    // 库存状态筛选（低库存阈值优先取商品自定义 low_stock_amount，未设置时回退 10）
    if (StringUtils.isNotBlank(stockStatus)) {
      if ("low".equals(stockStatus)) {
        wrapper.apply("stock > 0 AND stock <= COALESCE(CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.low_stock_amount')), '') AS SIGNED), 10)");
      } else if ("out".equals(stockStatus)) {
        wrapper.le(ProductEntity::getStock, 0);
      }
    }

    applySort(wrapper, sortBy, sortOrder);
    return productMapper.selectPage(new Page<>(page, size), wrapper);
  }

  @Override
  public Page<ProductEntity> listProductsByCategoryIds(int page, int size, List<Long> categoryIds, String sortBy, String sortOrder, String keyword, String status, Long brandIpId) {
    LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
        .in(categoryIds != null && !categoryIds.isEmpty(), ProductEntity::getCategoryId, categoryIds)
        .eq(brandIpId != null, ProductEntity::getBrandIpId, brandIpId)
        // 关键词搜索与主列表保持一致：名称 / SPU / SKU / GTIN
        .and(StringUtils.isNotBlank(keyword), w -> w
            .like(ProductEntity::getName, keyword)
            .or().like(ProductEntity::getSpuCode, keyword)
            .or().exists("(SELECT 1 FROM mo_product_sku s WHERE s.product_id = mo_product.id AND s.sku_code LIKE CONCAT('%', {0}, '%'))", keyword)
            .or().apply("JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.global_unique_id')) LIKE CONCAT('%', {0}, '%')", keyword));
    // 状态筛选：active=在售；inactive/draft/pending 基于真实审核记录派生
    applyStatusFilter(wrapper, status);
    applySort(wrapper, sortBy, sortOrder);
    return productMapper.selectPage(new Page<>(page, size), wrapper);
  }

  /**
   * 商品状态筛选（真实数据源）：
   * - active：on_sale=1
   * - pending：on_sale=0 且存在 PENDING/URGENT 审核记录
   * - inactive：on_sale=0 且存在已审核记录（APPROVED/REJECTED），且没有待审核记录
   * - draft：on_sale=0 且从未进入审核流程（无任何审核记录）
   * 说明：mo_product 本身没有草稿/审核状态字段，这里以审核流水表作为真实状态来源。
   * 审核通过前商品默认不对外销售（on_sale=0），因此四个 Tab 可以保持互斥且与计数口径一致。
   */
  private void applyStatusFilter(LambdaQueryWrapper<ProductEntity> wrapper, String status) {
    if (StringUtils.isBlank(status)) {
      return;
    }
    switch (status) {
      case "pending":
        wrapper.eq(ProductEntity::getOnSale, false)
            .inSql(ProductEntity::getId,
                "SELECT product_id FROM mo_product_approval WHERE status IN ('PENDING','URGENT')");
        break;
      case "active":
        wrapper.eq(ProductEntity::getOnSale, true);
        break;
      case "inactive":
        wrapper.eq(ProductEntity::getOnSale, false)
            .inSql(ProductEntity::getId,
                "SELECT product_id FROM mo_product_approval WHERE status IN ('APPROVED','REJECTED')")
            .notInSql(ProductEntity::getId,
                "SELECT product_id FROM mo_product_approval WHERE status IN ('PENDING','URGENT')");
        break;
      case "draft":
        wrapper.eq(ProductEntity::getOnSale, false)
            .notInSql(ProductEntity::getId, "SELECT product_id FROM mo_product_approval");
        break;
      default:
        // 未知状态按在售处理，保持与历史行为一致（非 active 视为下架）
        wrapper.eq(ProductEntity::getOnSale, "active".equals(status));
        break;
    }
  }

  /**
   * 统一排序规则。
   * <p>
   * 补充 createdAt / updatedAt / menuOrder 三种 APP 端常用排序：
   * - menuOrder 读取 attributes JSON 中的 menu_order，未填写按 0 处理
   * - 未知排序字段回退按创建时间倒序，避免 SQL 无 ORDER BY 导致分页结果不稳定
   */
  private void applySort(LambdaQueryWrapper<ProductEntity> wrapper, String sortBy, String sortOrder) {
    if (StringUtils.isBlank(sortBy)) {
      wrapper.orderByDesc(ProductEntity::getCreateTime);
      return;
    }
    boolean asc = !"desc".equalsIgnoreCase(sortOrder);
    if ("price".equalsIgnoreCase(sortBy)) {
      wrapper.orderBy(true, asc, ProductEntity::getPrice);
    } else if ("createTime".equalsIgnoreCase(sortBy) || "create_time".equalsIgnoreCase(sortBy)
        || "createdAt".equalsIgnoreCase(sortBy) || "created_at".equalsIgnoreCase(sortBy)) {
      wrapper.orderBy(true, asc, ProductEntity::getCreateTime);
    } else if ("updateTime".equalsIgnoreCase(sortBy) || "update_time".equalsIgnoreCase(sortBy)
        || "updatedAt".equalsIgnoreCase(sortBy)) {
      wrapper.orderBy(true, asc, ProductEntity::getUpdateTime);
    } else if ("sales".equalsIgnoreCase(sortBy)) {
      wrapper.orderBy(true, asc, ProductEntity::getSales);
    } else if ("menuOrder".equalsIgnoreCase(sortBy) || "menu_order".equalsIgnoreCase(sortBy)) {
      wrapper.last("ORDER BY COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.menu_order')) AS SIGNED), 0) "
          + (asc ? "ASC" : "DESC") + ", id ASC");
    } else {
      wrapper.orderByDesc(ProductEntity::getCreateTime);
    }
  }

  /**
   * 列表查询结果二次填充：为每个商品补齐 images[]（mo_product_image 关联表）。
   * <p>
   * 关键性能考量：
   * 1) selectPage 一次拉一批 ProductEntity，再批量查 images（避免 N+1）
   * 2) 仅取每商品 sort 最小的一张作为封面图，减少序列化体积
   * 3) 使用 in (productIds) 一次往返，分组在 Java 层
   */
  public java.util.Map<Long, List<ProductImageEntity>> getImagesByProductIds(java.util.List<Long> productIds) {
    if (productIds == null || productIds.isEmpty()) return java.util.Collections.emptyMap();
    List<ProductImageEntity> all = productImageMapper.selectList(
        new LambdaQueryWrapper<ProductImageEntity>()
            .in(ProductImageEntity::getProductId, productIds)
            .orderByAsc(ProductImageEntity::getSort));
    java.util.Map<Long, List<ProductImageEntity>> map = new java.util.HashMap<>();
    for (ProductImageEntity img : all) {
      map.computeIfAbsent(img.getProductId(), k -> new java.util.ArrayList<>()).add(img);
    }
    return map;
  }

  /**
   * 批量获取多个商品的 SKU 列表（用于商品列表接口填充 SKU 列，避免 N+1）。
   * <p>
   * 排序：按 id ASC 保证多个 SKU 时返回顺序稳定，前端取第一条作为"主 SKU"展示。
   */
  @Override
  public java.util.Map<Long, List<ProductSkuEntity>> getSkusByProductIds(java.util.List<Long> productIds) {
    if (productIds == null || productIds.isEmpty()) return java.util.Collections.emptyMap();
    List<ProductSkuEntity> all = productSkuMapper.selectList(
        new LambdaQueryWrapper<ProductSkuEntity>()
            .in(ProductSkuEntity::getProductId, productIds)
            .orderByAsc(ProductSkuEntity::getId));
    java.util.Map<Long, List<ProductSkuEntity>> map = new java.util.HashMap<>();
    for (ProductSkuEntity sku : all) {
      map.computeIfAbsent(sku.getProductId(), k -> new java.util.ArrayList<>()).add(sku);
    }
    return map;
  }

  @Override
  public ProductEntity getProductDetail(Long productId) {
    ProductEntity product = productMapper.selectById(productId);
    if (product == null) {
      throw new IllegalArgumentException("商品不存在");
    }
    return product;
  }

  @Override
  public List<ProductSkuEntity> getSkusByProductId(Long productId) {
    return productSkuMapper.selectList(
        new LambdaQueryWrapper<ProductSkuEntity>()
            .eq(ProductSkuEntity::getProductId, productId));
  }

  @Override
  public List<ProductImageEntity> getImagesByProductId(Long productId) {
    return productImageMapper.selectList(
        new LambdaQueryWrapper<ProductImageEntity>()
            .eq(ProductImageEntity::getProductId, productId)
            .orderByAsc(ProductImageEntity::getSort));
  }

  @Override
  public ProductEntity getProductWithDetails(Long productId) {
    ProductEntity product = getProductDetail(productId);
    List<ProductSkuEntity> skus = getSkusByProductId(productId);
    List<ProductImageEntity> images = getImagesByProductId(productId);
    product.setSkus(skus);
    product.setImages(images);
    // 预售配置：后台编辑页回显 + APP 详情页展示预售信息
    product.setPresaleInfo(getPresaleByProductId(productId));
    return product;
  }

  @Override
  public ProductPresaleEntity getPresaleByProductId(Long productId) {
    if (productId == null) {
      return null;
    }
    return productPresaleMapper.selectOne(
        new LambdaQueryWrapper<ProductPresaleEntity>()
            .eq(ProductPresaleEntity::getProductId, productId)
            .last("LIMIT 1"));
  }

  @Override
  public ProductEntity getProductWithDetailsBySlug(String slug) {
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("商品短链不能为空");
    }
    // slug 存储在 attributes JSON 中，MySQL 的 JSON_EXTRACT 可直接用作条件
    ProductEntity product = productMapper.selectOne(
        new LambdaQueryWrapper<ProductEntity>()
            .apply("JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.slug')) = {0}", slug.trim())
            .last("LIMIT 1"));
    if (product == null) {
      throw new IllegalArgumentException("商品不存在");
    }
    product.setSkus(getSkusByProductId(product.getId()));
    product.setImages(getImagesByProductId(product.getId()));
    // 分享链接进入同样需要展示预售信息
    product.setPresaleInfo(getPresaleByProductId(product.getId()));
    return product;
  }

  @Override
  @Transactional
  public ProductEntity createProduct(Map<String, Object> body) {
    ProductEntity entity = new ProductEntity();
    entity.setName((String) body.get("name"));
    if (body.get("price") != null) {
      entity.setPrice(new java.math.BigDecimal(body.get("price").toString()));
    }
    if (body.get("originalPrice") != null) {
      entity.setOriginalPrice(new java.math.BigDecimal(body.get("originalPrice").toString()));
    }
    // 售价留空时按原价出售（与编辑页“留空则使用原价出售”的提示保持一致）
    if (entity.getPrice() == null && entity.getOriginalPrice() != null) {
      entity.setPrice(entity.getOriginalPrice());
    }
    // 支持 category(前端字符串) 和 categoryId(后端Long) 两种格式
    if (body.get("categoryId") != null) {
      entity.setCategoryId(Long.valueOf(body.get("categoryId").toString()));
    } else if (body.get("category") != null) {
      entity.setCategoryId(getCategoryIdFromName((String) body.get("category")));
    }
    // 品牌：前端传 brandId（编辑页手动输入品牌名称后由后端解析为品牌 ID，可为空）
    Object createBrandObj = body.get("brandId");
    if (createBrandObj == null) createBrandObj = body.get("brand_id");
    if (createBrandObj != null && !createBrandObj.toString().isBlank()) {
      entity.setBrandId(Long.valueOf(createBrandObj.toString()));
    }
    entity.setMainImage((String) body.get("mainImage"));
    // 支持 description(前端) 和 detail(后端) 两种格式
    String detail = (String) body.get("description");
    if (detail == null) detail = (String) body.get("detail");
    entity.setDetail(detail);
    entity.setStock(body.get("stock") != null ? Integer.valueOf(body.get("stock").toString()) : 0);
    // 支持 status(前端boolean) 和 onSale(后端Boolean) 两种格式
    if (body.containsKey("status")) {
      entity.setOnSale((Boolean) body.get("status"));
    } else {
      entity.setOnSale((Boolean) body.getOrDefault("onSale", true));
    }
    // 支持 sku(前端) 和 spuCode(后端) 两种格式
    String spuCode = (String) body.get("sku");
    if (spuCode == null) spuCode = (String) body.get("spuCode");
    if (spuCode == null || spuCode.isEmpty()) {
      // 前端未传时自动生成基于时间戳的 SPU 编码，避免 NOT NULL 字段写入失败
      spuCode = "SPU" + System.currentTimeMillis();
    }
    entity.setSpuCode(spuCode);

    // === WooCommerce 对齐新增字段 ===
    // 简短描述：支持 shortDetail/short_detail 两种 key
    String shortDesc = (String) body.get("shortDetail");
    if (shortDesc == null) shortDesc = (String) body.get("short_detail");
    entity.setShortDetail(shortDesc);

    // 标签：支持 tags 字符串
    entity.setTags((String) body.get("tags"));

    // attributes JSON：存储 dimensions 等扩展字段
    Object attrsObj = body.get("attributes");
    if (attrsObj != null) {
      entity.setAttributes(attrsObj.toString());
      // slug 短链唯一性校验（SPU 之外，slug 需要全局唯一才能按短链定位商品）
      checkSlugUnique(null, body);
    }

    // 产品类型：支持 productType/product_type，默认 simple
    String pType = (String) body.get("productType");
    if (pType == null) pType = (String) body.get("product_type");
    entity.setProductType(pType != null ? pType : "simple");

    // 库存管理：支持 manageStock/manage_stock
    Object ms = body.get("manageStock");
    if (ms == null) ms = body.get("manage_stock");
    if (ms instanceof Boolean) entity.setManageStock((Boolean) ms);
    else if (ms != null) entity.setManageStock(Boolean.valueOf(ms.toString()));

    // 库存状态：支持 stockStatus/stock_status
    String ss = (String) body.get("stockStatus");
    if (ss == null) ss = (String) body.get("stock_status");
    entity.setStockStatus(ss != null ? ss : "IN_STOCK");

    // 积分兑换配置：开关 + 兑换所需积分 + 独立兑换库存（默认关闭/0，不影响现有商品）
    entity.setAllowPointsExchange(parseBool(body.get("allowPointsExchange"), false));
    entity.setPointsPrice(parseInt(body.get("pointsPrice"), 0));
    entity.setPointsStock(parseInt(body.get("pointsStock"), 0));

    // 重量：支持 weight
    if (body.get("weight") != null) {
      try {
        entity.setWeight(new java.math.BigDecimal(body.get("weight").toString()));
      } catch (NumberFormatException e) {
        log.warn("Invalid weight value from frontend: {}", body.get("weight"));
      }
    }

    // SPU编码唯一性校验
    if (entity.getSpuCode() != null && !entity.getSpuCode().isEmpty()) {
      Long existingCount = productMapper.selectCount(
        new LambdaQueryWrapper<ProductEntity>()
          .eq(ProductEntity::getSpuCode, entity.getSpuCode()));
      if (existingCount > 0) {
        throw new IllegalArgumentException("SPU编码已存在: " + entity.getSpuCode());
      }
    }

    entity.setCreateTime(java.time.LocalDateTime.now());
    entity.setUpdateTime(java.time.LocalDateTime.now());
    productMapper.insert(entity);

    // 保存商品图库到 mo_product_image（对齐 WC images[]）
    String syncedMainImage = body.containsKey("images") ? saveImages(entity.getId(), body) : null;
    if (syncedMainImage != null) {
      entity.setMainImage(syncedMainImage);
      productMapper.updateById(entity);
    }

    // 变体商品 SKU 落库：把 attributes.variations 拆成 mo_product_sku 行
    saveSkusForVariations(entity.getId(), entity.getSpuCode(), body);

    // 预售配置落库（body 未带 presale 时不处理，保持无预售）
    savePresale(entity.getId(), entity, body.get("presale"));

    // 事务提交后异步推送到 WooCommerce（避免阻塞主流程与事务回滚）
    registerWooCommercePush(entity, "create");
    return entity;
  }

  @Override
  @Transactional
  public ProductEntity updateProduct(Long id, Map<String, Object> body) {
    ProductEntity entity = productMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("商品不存在: " + id);
    }

    if (body.get("name") != null) entity.setName((String) body.get("name"));
    // 先写原价，便于售价被清空时回退到原价
    if (body.get("originalPrice") != null) entity.setOriginalPrice(new java.math.BigDecimal(body.get("originalPrice").toString()));
    if (body.containsKey("price")) {
      // 售价显式传 null/空 = 清空促销价，按原价出售（之前会被忽略，导致旧促销价残留）
      Object priceObj = body.get("price");
      if (priceObj != null && !priceObj.toString().isBlank()) {
        entity.setPrice(new java.math.BigDecimal(priceObj.toString()));
      } else {
        // price 为 NOT NULL 列：原价也缺失时置 0，避免旧促销价残留
        entity.setPrice(entity.getOriginalPrice() != null ? entity.getOriginalPrice() : java.math.BigDecimal.ZERO);
      }
    }
    // 支持 category(前端字符串) 和 categoryId(后端Long) 两种格式
    if (body.get("categoryId") != null) {
      entity.setCategoryId(Long.valueOf(body.get("categoryId").toString()));
    } else if (body.get("category") != null) {
      entity.setCategoryId(getCategoryIdFromName((String) body.get("category")));
    }
    // 品牌：请求携带时更新，空值表示清空品牌
    if (body.containsKey("brandId") || body.containsKey("brand_id")) {
      Object brandObj = body.containsKey("brandId") ? body.get("brandId") : body.get("brand_id");
      Long brandId = (brandObj == null || brandObj.toString().isBlank())
          ? null : Long.valueOf(brandObj.toString());
      // 记录原品牌：updateById 默认跳过 null 字段，只有原值非空且本次要清空时才需显式 SET
      Long oldBrandId = entity.getBrandId();
      entity.setBrandId(brandId);
      if (brandId == null && oldBrandId != null) {
        productMapper.update(null, new LambdaUpdateWrapper<ProductEntity>()
            .eq(ProductEntity::getId, id)
            .set(ProductEntity::getBrandId, null));
      }
    }
    if (body.get("mainImage") != null) entity.setMainImage((String) body.get("mainImage"));
    if (body.get("description") != null) entity.setDetail((String) body.get("description"));
    else if (body.get("detail") != null) entity.setDetail((String) body.get("detail"));
    if (body.get("stock") != null) entity.setStock(Integer.valueOf(body.get("stock").toString()));
    if (body.containsKey("status")) entity.setOnSale((Boolean) body.get("status"));
    else if (body.containsKey("onSale")) entity.setOnSale((Boolean) body.get("onSale"));
    String spuCode = (String) body.get("sku");
    if (spuCode == null) spuCode = (String) body.get("spuCode");
    if (spuCode != null && !spuCode.isEmpty()) {
      Long existingCount = productMapper.selectCount(
        new LambdaQueryWrapper<ProductEntity>()
          .eq(ProductEntity::getSpuCode, spuCode)
          .ne(ProductEntity::getId, id));
      if (existingCount > 0) {
        throw new IllegalArgumentException("SPU编码已被其他商品使用: " + spuCode);
      }
      entity.setSpuCode(spuCode);
    }
    String shortDesc = (String) body.get("shortDetail");
    if (shortDesc == null) shortDesc = (String) body.get("short_description");
    if (shortDesc != null) entity.setShortDetail(shortDesc);
    if (body.containsKey("tags")) entity.setTags((String) body.get("tags"));
    if (body.containsKey("attributes") && body.get("attributes") != null) {
      entity.setAttributes(body.get("attributes").toString());
      checkSlugUnique(id, body);
    }
    if (body.containsKey("productType")) entity.setProductType((String) body.get("productType"));
    else if (body.containsKey("product_type")) entity.setProductType((String) body.get("product_type"));
    Object ms = body.get("manageStock");
    if (ms == null) ms = body.get("manage_stock");
    if (ms instanceof Boolean) entity.setManageStock((Boolean) ms);
    else if (ms != null) entity.setManageStock(Boolean.valueOf(ms.toString()));
    if (body.containsKey("stockStatus")) entity.setStockStatus((String) body.get("stockStatus"));
    else if (body.containsKey("stock_status")) entity.setStockStatus((String) body.get("stock_status"));
    // 积分兑换配置：仅在请求携带时更新，避免部分更新把已配置的积分价清空
    if (body.containsKey("allowPointsExchange")) {
      entity.setAllowPointsExchange(parseBool(body.get("allowPointsExchange"), false));
    }
    if (body.containsKey("pointsPrice")) {
      entity.setPointsPrice(parseInt(body.get("pointsPrice"), 0));
    }
    if (body.containsKey("pointsStock")) {
      entity.setPointsStock(parseInt(body.get("pointsStock"), 0));
    }
    if (body.get("weight") != null) {
      try {
        entity.setWeight(new java.math.BigDecimal(body.get("weight").toString()));
      } catch (NumberFormatException we) {
        log.warn("Invalid weight value from frontend: {}", body.get("weight"));
      }
    }

    // 商品图库先更新（如果 body 中含 images 则覆盖），返回值用于同步主图
    String syncedMainImage = body.containsKey("images") ? saveImages(id, body) : null;
    if (syncedMainImage != null) {
      entity.setMainImage(syncedMainImage);
    }

    productMapper.updateById(entity);

    // 变体商品 SKU 落库：attributes（含变体）变化时全量重建 mo_product_sku
    if (body.containsKey("attributes")) {
      saveSkusForVariations(id, entity.getSpuCode(), body);
    }

    // 预售配置落库（body 未带 presale 时不处理）
    savePresale(id, entity, body.get("presale"));

    // 事务提交后异步推送到 WooCommerce（避免阻塞主流程与事务回滚）
    registerWooCommercePush(entity, "update");
    return entity;
  }

  /**
   * 保存商品预售配置（1:1）。
   * <p>
   * 前端约定：body.presale = { enabled, presaleType, presalePrice, ... }。
   * - presale 缺省：不处理（保持现状）
   * - enabled=false：删除该商品的预售配置
   * - enabled=true：按 presaleType 校验后新增或更新
   */
  private void savePresale(Long productId, ProductEntity product, Object presaleObj) {
    // 未传 presale：说明客户端本次不涉及预售，保持库中原状
    if (presaleObj == null) {
      return;
    }
    if (!(presaleObj instanceof Map)) {
      throw new IllegalArgumentException("预售配置格式不正确");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) presaleObj;
    ProductPresaleEntity existing = getPresaleByProductId(productId);

    // enabled 缺省视为开启（兼容只传预售字段的场景）
    Object enabledObj = body.get("enabled");
    boolean enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));
    if (!enabled) {
      if (existing != null) {
        // 存在未完结定金单时禁止删除：删掉配置会让定金单失去 balance_end/deposit_refundable，
        // 导致尾款逾期退定金任务找不到配置，订单永远停留在已付定金状态
        assertNoActiveDepositOrders(existing.getId());
        productPresaleMapper.deleteById(existing.getId());
      }
      return;
    }

    ProductPresaleEntity presale = new ProductPresaleEntity();
    presale.setProductId(productId);
    String type = strOf(body.get("presaleType"));
    presale.setPresaleType(type != null ? type.toUpperCase() : PRESALE_TYPE_FULL);
    presale.setPresalePrice(decimalOf(body.get("presalePrice")));
    presale.setStartTime(dateTimeOf(body.get("startTime")));
    presale.setEndTime(dateTimeOf(body.get("endTime")));
    presale.setShipDate(dateOf(body.get("shipDate")));
    presale.setStockLimit(intOf(body.get("stockLimit")));
    // 已售数量由订单侧维护：编辑商品时保留库中原值，避免后台误改
    // sold_count 为 NOT NULL 列，必须保证写入非 null
    Integer soldCount = existing != null ? existing.getSoldCount() : intOf(body.get("soldCount"));
    presale.setSoldCount(soldCount != null ? soldCount : 0);
    presale.setLimitPerUser(intOf(body.get("limitPerUser")));
    presale.setAutoCancelMinutes(intOf(body.get("autoCancelMinutes")));
    presale.setDepositAmount(decimalOf(body.get("depositAmount")));
    presale.setDepositOffset(decimalOf(body.get("depositOffset")));
    presale.setBalanceStart(dateTimeOf(body.get("balanceStart")));
    presale.setBalanceEnd(dateTimeOf(body.get("balanceEnd")));
    Object refundable = body.get("depositRefundable");
    presale.setDepositRefundable(refundable == null || Boolean.parseBoolean(String.valueOf(refundable)));
    presale.setNoticeText(strOf(body.get("noticeText")));
    presale.setCompensationDays(intOf(body.get("compensationDays")));
    presale.setCompensationPercent(decimalOf(body.get("compensationPercent")));

    // 校验 + 归一化（清理与当前类型无关的字段）
    validatePresale(presale, product);
    presale.setStatus(resolvePresaleStatus(presale));

    if (existing == null) {
      productPresaleMapper.insert(presale);
      return;
    }
    // 更新：显式 set 每一列，允许把定金/尾款等字段置空
    // （全局 update-strategy=not_null 会让 updateById 跳过 null，导致切换预售类型后旧值残留）
    LambdaUpdateWrapper<ProductPresaleEntity> uw = new LambdaUpdateWrapper<ProductPresaleEntity>()
        .eq(ProductPresaleEntity::getId, existing.getId())
        .set(ProductPresaleEntity::getPresaleType, presale.getPresaleType())
        .set(ProductPresaleEntity::getStatus, presale.getStatus())
        .set(ProductPresaleEntity::getPresalePrice, presale.getPresalePrice())
        .set(ProductPresaleEntity::getStartTime, presale.getStartTime())
        .set(ProductPresaleEntity::getEndTime, presale.getEndTime())
        .set(ProductPresaleEntity::getShipDate, presale.getShipDate())
        .set(ProductPresaleEntity::getStockLimit, presale.getStockLimit())
        .set(ProductPresaleEntity::getSoldCount, presale.getSoldCount())
        .set(ProductPresaleEntity::getLimitPerUser, presale.getLimitPerUser())
        .set(ProductPresaleEntity::getAutoCancelMinutes, presale.getAutoCancelMinutes())
        .set(ProductPresaleEntity::getDepositAmount, presale.getDepositAmount())
        .set(ProductPresaleEntity::getDepositOffset, presale.getDepositOffset())
        .set(ProductPresaleEntity::getBalanceStart, presale.getBalanceStart())
        .set(ProductPresaleEntity::getBalanceEnd, presale.getBalanceEnd())
        .set(ProductPresaleEntity::getDepositRefundable, presale.getDepositRefundable())
        .set(ProductPresaleEntity::getNoticeText, presale.getNoticeText())
        .set(ProductPresaleEntity::getCompensationDays, presale.getCompensationDays())
        .set(ProductPresaleEntity::getCompensationPercent, presale.getCompensationPercent())
        .set(ProductPresaleEntity::getUpdateTime, java.time.LocalDateTime.now());
    productPresaleMapper.update(null, uw);
  }

  /**
   * 校验该预售下是否仍有"未完结"的定金单，删除配置前调用。
   * <p>
   * 未完结定义：
   * 1) 定金单待支付（PENDING_PAY）；
   * 2) 定金已支付（PAID），但还没有已生效的尾款单（不存在，或尾款单仍待支付/已取消）。
   * <p>
   * 尾款已支付或尾款已退款说明交易已进入履约/结束阶段，不再阻止删除配置；
   * 否则定金单被删掉配置后就永远等不到退定金任务处理。
   */
  private void assertNoActiveDepositOrders(Long presaleId) {
    List<OrderEntity> depositOrders = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .select(OrderEntity::getId)
            .eq(OrderEntity::getPresaleId, presaleId)
            .eq(OrderEntity::getOrderType, "PRESALE")
            .eq(OrderEntity::getPresaleStage, "DEPOSIT")
            .in(OrderEntity::getStatus, "PENDING_PAY", "PAID")
            .eq(OrderEntity::getDeleteStatus, 0));
    if (depositOrders.isEmpty()) {
      return;
    }
    List<Long> depositIds = depositOrders.stream().map(OrderEntity::getId).toList();
    // 已生效尾款单：非"待支付/已取消"即视为已履约或已退款结束
    List<OrderEntity> effectiveBalances = orderMapper.selectList(
        new LambdaQueryWrapper<OrderEntity>()
            .select(OrderEntity::getParentOrderId)
            .in(OrderEntity::getParentOrderId, depositIds)
            .notIn(OrderEntity::getStatus, "PENDING_PAY", "CANCELLED"));
    Set<Long> finishedDepositIds = effectiveBalances.stream()
        .map(OrderEntity::getParentOrderId)
        .collect(Collectors.toSet());
    long active = depositOrders.stream()
        .filter(order -> !finishedDepositIds.contains(order.getId()))
        .count();
    if (active > 0) {
      throw new IllegalArgumentException(
          "该商品存在 " + active + " 笔未完结的定金预售订单，暂不能关闭预售");
    }
  }

  /**
   * 预售配置校验：类型合法性、时间区间、库存、定金/尾款金额关系。
   * 校验失败抛 IllegalArgumentException，由 Controller 转成 400。
   */
  private void validatePresale(ProductPresaleEntity presale, ProductEntity product) {
    String type = presale.getPresaleType();
    if (!PRESALE_TYPE_DISPLAY.equals(type) && !PRESALE_TYPE_FULL.equals(type) && !PRESALE_TYPE_DEPOSIT.equals(type)) {
      throw new IllegalArgumentException("不支持的预售类型: " + type);
    }
    if (presale.getStartTime() != null && presale.getEndTime() != null
        && !presale.getEndTime().isAfter(presale.getStartTime())) {
      throw new IllegalArgumentException("预售结束时间必须晚于预售开始时间");
    }
    if (presale.getStockLimit() != null && presale.getStockLimit() <= 0) {
      throw new IllegalArgumentException("预售库存上限必须大于 0");
    }
    if (presale.getSoldCount() != null && presale.getSoldCount() < 0) {
      throw new IllegalArgumentException("已售预售数量不能为负数");
    }
    if (presale.getSoldCount() != null && presale.getStockLimit() != null
        && presale.getSoldCount() > presale.getStockLimit()) {
      throw new IllegalArgumentException("已售预售数量不能超过预售库存上限");
    }
    if (presale.getLimitPerUser() != null && presale.getLimitPerUser() <= 0) {
      throw new IllegalArgumentException("每人限购数量必须大于 0");
    }
    if (presale.getAutoCancelMinutes() != null && presale.getAutoCancelMinutes() <= 0) {
      throw new IllegalArgumentException("未付款自动取消时间必须大于 0 分钟");
    }

    // 全款 / 展示型不涉及定金与尾款，清空相关字段，避免切换类型后旧值残留
    if (!PRESALE_TYPE_DEPOSIT.equals(type)) {
      presale.setDepositAmount(null);
      presale.setDepositOffset(null);
      presale.setBalanceStart(null);
      presale.setBalanceEnd(null);
      presale.setDepositRefundable(true);
    }
    // 展示型预售只展示时间与发货日期，不校验价格
    if (PRESALE_TYPE_DISPLAY.equals(type)) {
      presale.setPresalePrice(null);
      return;
    }

    if (presale.getPresalePrice() == null || presale.getPresalePrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("预售价格必须大于 0");
    }
    // 预售价格不得高于商品售价（避免预售比现货更贵）
    if (product.getPrice() != null && presale.getPresalePrice().compareTo(product.getPrice()) > 0) {
      throw new IllegalArgumentException("预售价格不能高于商品售价 " + product.getPrice().toPlainString());
    }

    if (!PRESALE_TYPE_DEPOSIT.equals(type)) {
      return;
    }
    if (presale.getDepositAmount() == null || presale.getDepositAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("定金预售必须填写大于 0 的定金金额");
    }
    if (presale.getDepositAmount().compareTo(presale.getPresalePrice()) >= 0) {
      throw new IllegalArgumentException("定金金额必须小于预售价格");
    }
    // 未填抵扣金额时默认按"定金全额抵扣"处理
    if (presale.getDepositOffset() == null) {
      presale.setDepositOffset(presale.getDepositAmount());
    }
    if (presale.getDepositOffset().compareTo(presale.getPresalePrice()) > 0) {
      throw new IllegalArgumentException("定金抵扣金额不能高于预售价格");
    }
    if (presale.getPresalePrice().subtract(presale.getDepositOffset()).compareTo(java.math.BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("尾款金额必须大于 0（预售价 - 定金抵扣金额）");
    }
    // 尾款必须在预售结束后才能支付
    if (presale.getBalanceStart() != null && presale.getEndTime() != null
        && !presale.getBalanceStart().isAfter(presale.getEndTime())) {
      throw new IllegalArgumentException("尾款支付开始时间必须晚于预售结束时间");
    }
    if (presale.getBalanceStart() != null && presale.getBalanceEnd() != null
        && !presale.getBalanceEnd().isAfter(presale.getBalanceStart())) {
      throw new IllegalArgumentException("尾款支付结束时间必须晚于开始时间");
    }
  }

  /** 按时间与库存推导预售状态（保存时统一计算，避免前端随意写入状态） */
  private String resolvePresaleStatus(ProductPresaleEntity presale) {
    java.time.LocalDateTime now = java.time.LocalDateTime.now();
    java.time.LocalDateTime start = presale.getStartTime();
    java.time.LocalDateTime end = presale.getEndTime();
    // 未配置预售时间：视为草稿
    if (start == null && end == null) {
      return "DRAFT";
    }
    // 已过结束时间：已结束（优先级最高，避免已结束活动仍显示售罄）
    if (end != null && now.isAfter(end)) {
      return "ENDED";
    }
    // 未到开始时间：待开始
    if (start != null && now.isBefore(start)) {
      return "SCHEDULED";
    }
    // 库存售罄
    if (presale.getStockLimit() != null && presale.getSoldCount() != null
        && presale.getSoldCount() >= presale.getStockLimit()) {
      return "SOLD_OUT";
    }
    return "ONGOING";
  }

  /**
   * 按时间/库存刷新预售状态。
   * <p>
   * 只更新状态与读取时一致的记录（乐观条件），避免覆盖并发下单刚写入的 SOLD_OUT。
   */
  @Override
  public int refreshPresaleStatuses() {
    List<ProductPresaleEntity> presales = productPresaleMapper.selectList(
        new LambdaQueryWrapper<ProductPresaleEntity>());
    int changed = 0;
    for (ProductPresaleEntity presale : presales) {
      String next = resolvePresaleStatus(presale);
      if (next.equals(presale.getStatus())) {
        continue;
      }
      LambdaUpdateWrapper<ProductPresaleEntity> uw = new LambdaUpdateWrapper<ProductPresaleEntity>()
          .eq(ProductPresaleEntity::getId, presale.getId());
      // 状态可能为 null（历史数据），条件必须区分 is null / eq，否则永远匹配不到
      if (presale.getStatus() == null) {
        uw.isNull(ProductPresaleEntity::getStatus);
      } else {
        uw.eq(ProductPresaleEntity::getStatus, presale.getStatus());
      }
      uw.set(ProductPresaleEntity::getStatus, next)
          .set(ProductPresaleEntity::getUpdateTime, java.time.LocalDateTime.now());
      changed += productPresaleMapper.update(null, uw);
    }
    return changed;
  }

  /** 字符串取值：null / 空串统一返回 null */
  private String strOf(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  /** 金额取值：空串 / 非法值返回 null */
  private java.math.BigDecimal decimalOf(Object value) {
    String text = strOf(value);
    if (text == null) {
      return null;
    }
    try {
      return new java.math.BigDecimal(text);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("金额格式不正确: " + text);
    }
  }

  /** 整数取值：空串 / 非法值返回 null */
  private Integer intOf(Object value) {
    String text = strOf(value);
    if (text == null) {
      return null;
    }
    try {
      return Integer.valueOf(text);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("数字格式不正确: " + text);
    }
  }

  /** 日期时间取值：兼容 "yyyy-MM-dd HH:mm:ss" 与 ISO "yyyy-MM-ddTHH:mm:ss(.SSS)" */
  private java.time.LocalDateTime dateTimeOf(Object value) {
    String text = strOf(value);
    if (text == null) {
      return null;
    }
    String normalized = text.replace('T', ' ');
    if (normalized.length() > 19) {
      normalized = normalized.substring(0, 19);
    }
    for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"}) {
      try {
        return java.time.LocalDateTime.parse(normalized,
            java.time.format.DateTimeFormatter.ofPattern(pattern));
      } catch (java.time.format.DateTimeParseException ignored) {
        // 尝试下一种格式
      }
    }
    throw new IllegalArgumentException("时间格式不正确: " + text);
  }

  /** 日期取值：兼容 "yyyy-MM-dd" 与完整日期时间字符串 */
  private java.time.LocalDate dateOf(Object value) {
    String text = strOf(value);
    if (text == null) {
      return null;
    }
    if (text.length() > 10) {
      text = text.substring(0, 10);
    }
    try {
      return java.time.LocalDate.parse(text, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    } catch (java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("日期格式不正确: " + text);
    }
  }

  /**
   * 校验 attributes.slug 短链唯一性：APP 分享链接会按 slug 反查商品，重复会导致定位到错误商品。
   *
   * @param excludeId 更新时排除自身 id；新增传 null
   */
  private void checkSlugUnique(Long excludeId, Map<String, Object> body) {
    Object raw = body.get("attributes");
    if (raw == null) {
      return;
    }
    Map<String, Object> attrs;
    if (raw instanceof String text) {
      attrs = ProductAttributes.parse(text);
    } else {
      try {
        attrs = objectMapper.convertValue(raw, new TypeReference<Map<String, Object>>() {
        });
      } catch (Exception e) {
        return;
      }
    }
    String slug = ProductAttributes.str(attrs, "slug", "");
    if (slug.isEmpty()) {
      return;
    }
    Long count = productMapper.selectCount(
        new LambdaQueryWrapper<ProductEntity>()
            .apply("JSON_UNQUOTE(JSON_EXTRACT(attributes, '$.slug')) = {0}", slug)
            .ne(excludeId != null, ProductEntity::getId, excludeId));
    if (count != null && count > 0) {
      throw new IllegalArgumentException("商品短链(slug)已存在: " + slug);
    }
  }

  /**
   * 保存商品图库到 mo_product_image 表。
   * <p>
   * 输入格式（与前端 ImageUploader 输出一致）：
   *   body.images = [{ url: "...", name: "..." }, ...] 或纯字符串数组
   * <p>
   * 行为：
   *   1) 删除该商品所有旧图
   *   2) 按数组下标设置 sort（首张 sort=0 = 封面，对齐 WC images[0]）
   *   3) 同步更新 products.mainImage 为 images[0].url
   */
  private String saveImages(Long productId, Map<String, Object> body) {
    Object raw = body.get("images");
    if (raw == null) return null;

    // 清空旧图
    productImageMapper.delete(
        new LambdaQueryWrapper<ProductImageEntity>().eq(ProductImageEntity::getProductId, productId));

    List<ProductImageEntity> newImages = new java.util.ArrayList<>();
    if (raw instanceof List<?> list) {
      int sort = 0;
      for (Object item : list) {
        String url = null;
        String name = null;
        if (item instanceof Map<?, ?> m) {
          url = (String) m.get("url");
          name = (String) m.get("name");
        } else if (item instanceof String s) {
          url = s;
        }
        if (url == null || url.isBlank()) continue;
        ProductImageEntity ie = new ProductImageEntity();
        ie.setProductId(productId);
        ie.setUrl(url);
        ie.setSort(sort++);
        newImages.add(ie);
        if (sort == 1) name = name; // 保留原始名供后续拓展
      }
    }

    if (newImages.isEmpty()) {
      // 没有图片时返回 null，调用方会把 mainImage 同步清空
      return null;
    }

    for (ProductImageEntity ie : newImages) {
      productImageMapper.insert(ie);
    }

    // 返回 images[0].url，由调用方同步到 products.main_image
    return newImages.get(0).getUrl();
  }

  /**
   * 商品 SKU 落库：
   *   - variable 变体商品：把 attributes.variations 拆成多条 mo_product_sku 行（全量覆盖）
   *   - simple 简单商品：如果有 spuCode，落一条默认 SKU（sku_code=spuCode, price/stock 取商品主值）
   * <p>
   * 全量覆盖策略：先删旧 SKU 再重插，避免旧数据残留；主键每次重新生成（存量无历史引用，安全）。
   */
  private void saveSkusForVariations(Long productId, String spuCode, Map<String, Object> body) {
    String pType = (String) body.get("productType");
    if (pType == null) pType = (String) body.get("product_type");
    boolean isVariable = "variable".equalsIgnoreCase(pType);

    // === 简单商品：落一条默认 SKU ===
    if (!isVariable) {
      // 先清旧 SKU（商品类型可能从 variable 改成 simple，旧变体要清掉）
      productSkuMapper.delete(new LambdaQueryWrapper<ProductSkuEntity>()
          .eq(ProductSkuEntity::getProductId, productId));

      // 没有 spuCode 就不生成（没设 SKU 编码的简单商品保持原样）
      if (spuCode == null || spuCode.isBlank()) return;

      ProductEntity dbProduct = productMapper.selectById(productId);
      if (dbProduct == null) return;

      ProductSkuEntity sku = new ProductSkuEntity();
      sku.setProductId(productId);
      sku.setSkuCode(spuCode);
      sku.setPrice(dbProduct.getPrice());
      sku.setStock(dbProduct.getStock());
      sku.setSales(0);
      productSkuMapper.insert(sku);
      return;
    }

    // === 变体商品：拆 variations ===
    Object rawAttributes = body.get("attributes");
    if (rawAttributes == null) {
      productSkuMapper.delete(new LambdaQueryWrapper<ProductSkuEntity>()
          .eq(ProductSkuEntity::getProductId, productId));
      return;
    }

    try {
      JsonNode root = objectMapper.readTree(rawAttributes.toString());
      JsonNode variations = root.path("variations");
      if (!variations.isArray() || variations.isEmpty()) {
        // 变体商品但没有可用的变体：清空旧 SKU
        productSkuMapper.delete(new LambdaQueryWrapper<ProductSkuEntity>()
            .eq(ProductSkuEntity::getProductId, productId));
        return;
      }

      // 全量覆盖：先删旧，再按当前变体重建
      productSkuMapper.delete(new LambdaQueryWrapper<ProductSkuEntity>()
          .eq(ProductSkuEntity::getProductId, productId));

      // 记录本批次已用 SKU 编码，避免同商品内重复（联合唯一键 product_id + sku_code）
      java.util.Set<String> usedCodes = new java.util.HashSet<>();

      int idx = 0;
      for (JsonNode v : variations) {
        if (!v.isObject()) continue;
        // 禁用的变体不落库
        if (v.has("enabled") && !v.path("enabled").asBoolean()) continue;

        ProductSkuEntity sku = new ProductSkuEntity();
        sku.setProductId(productId);

        // SKU 编码：优先取变体手动填写的 sku，为空则用 SPU 编码 + 序号兜底；
        // 同商品内编码冲突则追加 "-(规格值)" 后缀（避免用户录入重复 SKU 编码被联合唯一键挡住）
        String code = v.path("sku").asText("");
        if (code == null || code.isBlank()) {
          code = spuCode + "-" + (idx + 1);
        }
        String finalCode = code;
        int suffixNum = 1;
        while (usedCodes.contains(finalCode)) {
          // 用规格值或变体序号作为后缀，让编码可读
          String suffix = buildSpecSuffix(v);
          finalCode = code + "-" + (suffix.isBlank() ? ("dup" + suffixNum++) : suffix);
        }
        usedCodes.add(finalCode);
        sku.setSkuCode(finalCode);

        // WooCommerce 变体 id：仅当为纯数字（真实 id）时落库，忽略前端临时 id（如 tmp_xxx）
        String varId = v.path("id").asText("");
        if (varId != null && varId.matches("\\d+")) {
          sku.setWooVariationId(Long.valueOf(varId));
        } else if (v.hasNonNull("wooVariationId")) {
          sku.setWooVariationId(v.path("wooVariationId").asLong());
        }

        // 规格描述：属性组合，如 "尺寸:S"
        sku.setSpec(buildSpecText(v));

        // 变体价格：优先促销价 salePrice，其次 regularPrice，回退商品价，最后回退 DB 当前商品价
        java.math.BigDecimal price = readBigDecimal(v, "salePrice");
        if (price == null) price = readBigDecimal(v, "regularPrice");
        if (price == null) price = readBigDecimal(body, "price");
        if (price == null) {
          ProductEntity dbProduct = productMapper.selectById(productId);
          if (dbProduct != null) price = dbProduct.getPrice();
        }
        if (price == null) {
          log.warn("变体 SKU 价格缺失，跳过变体 productId={} idx={}", productId, idx);
          continue;
        }
        sku.setPrice(price);

        // 变体库存：单独管理时用 stockQuantity，否则回退商品级 stock
        if (v.has("manageStock") && v.path("manageStock").asBoolean()) {
          sku.setStock(v.path("stockQuantity").asInt(0));
        } else {
          sku.setStock(readInt(body, "stock"));
        }
        sku.setSales(0);
        productSkuMapper.insert(sku);
        idx++;
      }
    } catch (Exception e) {
      // SKU 落库失败不应阻断商品本身保存，仅记录告警
      log.warn("变体 SKU 落库失败，productId={}, reason={}", productId, e.getMessage());
    }
  }

  /**
   * 从变体 JSON 中提取规格描述："属性名:属性值" 用 / 拼接。
   */
  private String buildSpecText(JsonNode variation) {
    JsonNode attrs = variation.path("attributes");
    if (!attrs.isArray() || attrs.isEmpty()) {
      return "";
    }
    List<String> parts = new java.util.ArrayList<>();
    for (JsonNode a : attrs) {
      parts.add(a.path("name").asText("") + ":" + a.path("value").asText(""));
    }
    return String.join(" / ", parts);
  }

  /**
   * 为冲突的 SKU 编码提供可读后缀：取最后一个规格值（去掉非法字符），如 "S"、"L"。
   */
  private String buildSpecSuffix(JsonNode variation) {
    JsonNode attrs = variation.path("attributes");
    if (!attrs.isArray() || attrs.isEmpty()) return "";
    // 取最后一个规格值作为后缀，便于区分
    String lastVal = "";
    for (JsonNode a : attrs) {
      String v = a.path("value").asText("").trim();
      if (!v.isBlank()) lastVal = v;
    }
    // 去掉特殊字符，只保留字母数字
    return lastVal.replaceAll("[^A-Za-z0-9]", "");
  }

  /**
   * 从 JsonNode 中安全读取指定数值字段为 BigDecimal，缺失或非法返回 null。
   */
  private java.math.BigDecimal readBigDecimal(JsonNode node, String field) {
    if (!node.hasNonNull(field)) return null;
    try {
      return new java.math.BigDecimal(node.path(field).asText(""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 从 body Map 中安全读取数值字段为 BigDecimal，缺失或非法返回 null。
   */
  private java.math.BigDecimal readBigDecimal(Map<String, Object> body, String field) {
    Object val = body.get(field);
    if (val == null) return null;
    try {
      return new java.math.BigDecimal(val.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 从 body Map 中安全读取整型字段（如 stock）。
   */
  private Integer readInt(Map<String, Object> body, String field) {
    Object val = body.get(field);
    if (val == null) return 0;
    try {
      return Integer.valueOf(val.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * 注册事务提交后的 WooCommerce 异步推送。
   * - 仅在当前线程已开启事务时才注册（保证 afterCommit 在 commit 后才执行）
   * - 通过新线程执行，避免 WooCommerce API 限流/超时阻塞调用方
   * - 推送失败只记录日志，不影响主流程
   */
  private void registerWooCommercePush(ProductEntity entity, String op) {
    if (entity == null) {
      return;
    }
    // 仅在有 wooProductId 时才走更新路径；首次创建交给 push 路径
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      final Long productId = entity.getId();
      final boolean alreadySynced = entity.getWooProductId() != null;
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          new Thread(() -> {
            try {
              if (alreadySynced) {
                boolean ok = wooCommerceSyncService.updateProductOnWooCommerce(entity);
                log.info("商品更新后推送到 WooCommerce: productId={}, op={}, success={}",
                    productId, op, ok);
              } else {
                Long wooId = wooCommerceSyncService.pushProductToWooCommerce(entity);
                log.info("商品创建后推送到 WooCommerce: productId={}, op={}, wooProductId={}",
                    productId, op, wooId);
              }
            } catch (Exception e) {
              log.error("商品{}后推送到 WooCommerce 失败: productId={}, reason={}",
                  op, productId, e.getMessage());
            }
          }, "woo-product-sync-" + productId).start();
        }
      });
    }
  }

  /**
   * 将前端分类名转换为分类ID（简单的映射关系，后续可改造为查数据库）
   */
  private Long getCategoryIdFromName(String categoryName) {
    if (categoryName == null || categoryName.isEmpty()) return null;
    return switch (categoryName) {
      case "health" -> 1L;
      case "food" -> 2L;
      case "beauty" -> 3L;
      case "daily" -> 4L;
      default -> {
        // 如果是纯数字字符串，直接解析
        try {
          yield Long.valueOf(categoryName);
        } catch (NumberFormatException e) {
          yield null;
        }
      }
    };
  }

  @Override
  @Transactional
  public ProductEntity toggleProductStatus(Long id) {
    ProductEntity entity = productMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("商品不存在: " + id);
    }
    entity.setOnSale(!entity.getOnSale());
    productMapper.updateById(entity);
    return entity;
  }

  @Override
  @Transactional
  public int batchProductAction(String action, List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (Long id : ids) {
      ProductEntity entity = productMapper.selectById(id);
      if (entity != null) {
        if ("shelf".equals(action)) {
          entity.setOnSale(true);
          productMapper.updateById(entity);
          count++;
        } else if ("unshelf".equals(action)) {
          entity.setOnSale(false);
          productMapper.updateById(entity);
          count++;
        } else if ("delete".equals(action)) {
          // 先删除关联的 SKU 和图片，避免外键约束失败
          productSkuMapper.delete(new LambdaQueryWrapper<ProductSkuEntity>()
              .eq(ProductSkuEntity::getProductId, id));
          productImageMapper.delete(new LambdaQueryWrapper<ProductImageEntity>()
              .eq(ProductImageEntity::getProductId, id));
          productMapper.deleteById(id);
          count++;
        }
      }
    }
    return count;
  }

  /** 宽松解析布尔值：兼容前端传 true/false、"true"/"1"/1 */
  private Boolean parseBool(Object value, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    String text = value.toString().trim();
    if (text.isEmpty()) {
      return defaultValue;
    }
    return "1".equals(text) || "true".equalsIgnoreCase(text);
  }

  /** 宽松解析整数：非法值回退默认值，避免编辑页脏数据导致保存失败 */
  private Integer parseInt(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.valueOf(value.toString().trim());
    } catch (NumberFormatException e) {
      log.warn("Invalid integer value from frontend: {}", value);
      return defaultValue;
    }
  }
}

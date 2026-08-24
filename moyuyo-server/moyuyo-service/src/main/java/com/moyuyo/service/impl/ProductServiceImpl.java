package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductImageEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductImageMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.ProductService;
import com.moyuyo.service.WooCommerceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;
  private final ProductImageMapper productImageMapper;
  // 注入 WooCommerce 同步服务：商品变更后自动推送到 WooCommerce
  private final WooCommerceSyncService wooCommerceSyncService;

  @Override
  public Page<ProductEntity> listProducts(int page, int size, Long categoryId, String sortBy, String sortOrder, String keyword, String status, String stockStatus, Long brandIpId) {
    LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<ProductEntity>()
        .eq(categoryId != null, ProductEntity::getCategoryId, categoryId)
        .eq(brandIpId != null, ProductEntity::getBrandIpId, brandIpId)
        .like(StringUtils.isNotBlank(keyword), ProductEntity::getName, keyword);

    // 状态筛选：active=在售, inactive=已下架, 不传则不过滤
    if (StringUtils.isNotBlank(status)) {
      wrapper.eq(ProductEntity::getOnSale, "active".equals(status));
    }

    // 库存状态筛选
    if (StringUtils.isNotBlank(stockStatus)) {
      if ("low".equals(stockStatus)) {
        wrapper.le(ProductEntity::getStock, 10).gt(ProductEntity::getStock, 0);
      } else if ("out".equals(stockStatus)) {
        wrapper.le(ProductEntity::getStock, 0);
      }
    }

    if (StringUtils.isNotBlank(sortBy)) {
      boolean asc = !"desc".equalsIgnoreCase(sortOrder);
      if ("price".equalsIgnoreCase(sortBy)) {
        wrapper.orderBy(true, asc, ProductEntity::getPrice);
      } else if ("createTime".equalsIgnoreCase(sortBy) || "create_time".equalsIgnoreCase(sortBy)) {
        wrapper.orderBy(true, asc, ProductEntity::getCreateTime);
      } else if ("sales".equalsIgnoreCase(sortBy)) {
        wrapper.orderBy(true, asc, ProductEntity::getSales);
      }
    } else {
      wrapper.orderByDesc(ProductEntity::getCreateTime);
    }

    return productMapper.selectPage(new Page<>(page, size), wrapper);
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
    // 支持 category(前端字符串) 和 categoryId(后端Long) 两种格式
    if (body.get("categoryId") != null) {
      entity.setCategoryId(Long.valueOf(body.get("categoryId").toString()));
    } else if (body.get("category") != null) {
      entity.setCategoryId(getCategoryIdFromName((String) body.get("category")));
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
    saveImages(entity.getId(), body);

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
    if (body.get("price") != null) entity.setPrice(new java.math.BigDecimal(body.get("price").toString()));
    if (body.get("originalPrice") != null) entity.setOriginalPrice(new java.math.BigDecimal(body.get("originalPrice").toString()));
    // 支持 category(前端字符串) 和 categoryId(后端Long) 两种格式
    if (body.get("categoryId") != null) {
      entity.setCategoryId(Long.valueOf(body.get("categoryId").toString()));
    } else if (body.get("category") != null) {
      entity.setCategoryId(getCategoryIdFromName((String) body.get("category")));
    }
    if (body.get("mainImage") != null) entity.setMainImage((String) body.get("mainImage"));
    // 支持 description(前端) 和 detail(后端) 两种格式
    if (body.get("description") != null) entity.setDetail((String) body.get("description"));
    else if (body.get("detail") != null) entity.setDetail((String) body.get("detail"));
    if (body.get("stock") != null) entity.setStock(Integer.valueOf(body.get("stock").toString()));
    // 支持 status(前端boolean) 和 onSale(后端Boolean) 两种格式
    if (body.containsKey("status")) entity.setOnSale((Boolean) body.get("status"));
    else if (body.containsKey("onSale")) entity.setOnSale((Boolean) body.get("onSale"));
    // 支持 sku(前端) 和 spuCode(后端) 两种格式
    String spuCode = (String) body.get("sku");
    if (spuCode == null) spuCode = (String) body.get("spuCode");
    if (spuCode != null && !spuCode.isEmpty()) {
      // SPU编码唯一性校验（排除自身）
      Long existingCount = productMapper.selectCount(
        new LambdaQueryWrapper<ProductEntity>()
          .eq(ProductEntity::getSpuCode, spuCode)
          .ne(ProductEntity::getId, id));
      if (existingCount > 0) {
        throw new IllegalArgumentException("SPU编码已被其他商品使用: " + spuCode);
      }
      entity.setSpuCode(spuCode);
    }

    // === WooCommerce 对齐新增字段 ===
    // 简短描述
    String shortDesc = (String) body.get("shortDetail");
    if (shortDesc == null) shortDesc = (String) body.get("short_detail");
    if (shortDesc != null) entity.setShortDetail(shortDesc);

    // 标签
    if (body.containsKey("tags")) entity.setTags((String) body.get("tags"));

    // attributes JSON
    if (body.containsKey("attributes")) entity.setAttributes(body.get("attributes").toString());

    // 产品类型
    if (body.containsKey("productType")) entity.setProductType((String) body.get("productType"));
    else if (body.containsKey("product_type")) entity.setProductType((String) body.get("product_type"));

    // 库存管理
    Object ms = body.get("manageStock");
    if (ms == null) ms = body.get("manage_stock");
    if (ms instanceof Boolean) entity.setManageStock((Boolean) ms);
    else if (ms != null) entity.setManageStock(Boolean.valueOf(ms.toString()));

    // 库存状态
    if (body.containsKey("stockStatus")) entity.setStockStatus((String) body.get("stockStatus"));
    else if (body.containsKey("stock_status")) entity.setStockStatus((String) body.get("stock_status"));

    // 重量
    if (body.get("weight") != null) {
      try {
        entity.setWeight(new java.math.BigDecimal(body.get("weight").toString()));
      } catch (NumberFormatException e) {
        log.warn("Invalid weight value from frontend: {}", body.get("weight"));
      }
    }

    productMapper.updateById(entity);

    // 商品图库更新（如果 body 中含 images 则覆盖，否则保留旧图）
    if (body.containsKey("images")) {
      saveImages(id, body);
    }

    // 事务提交后异步推送到 WooCommerce（避免阻塞主流程与事务回滚）
    registerWooCommercePush(entity, "update");
    return entity;
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
  private void saveImages(Long productId, Map<String, Object> body) {
    Object raw = body.get("images");
    if (raw == null) return;

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
      // 没有图片时同步清空 mainImage
      ProductEntity e = productMapper.selectById(productId);
      if (e != null) {
        e.setMainImage(null);
        productMapper.updateById(e);
      }
      return;
    }

    for (ProductImageEntity ie : newImages) {
      productImageMapper.insert(ie);
    }

    // 同步 mainImage 为 images[0]
    ProductEntity e = productMapper.selectById(productId);
    if (e != null) {
      e.setMainImage(newImages.get(0).getUrl());
      productMapper.updateById(e);
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
}

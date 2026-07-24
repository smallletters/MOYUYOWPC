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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

  private final ProductMapper productMapper;
  private final ProductSkuMapper productSkuMapper;
  private final ProductImageMapper productImageMapper;

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
    if (spuCode != null && !spuCode.isEmpty()) {
      entity.setSpuCode(spuCode);
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

    productMapper.updateById(entity);
    return entity;
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
          productMapper.deleteById(id);
          count++;
        }
      }
    }
    return count;
  }
}

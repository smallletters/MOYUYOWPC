package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.CategoryEntity;
import com.moyuyo.dao.entity.FavoriteEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.CategoryMapper;
import com.moyuyo.dao.mapper.FavoriteMapper;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.admin.AdminProductAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 管理后台商品分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductAnalysisServiceImpl implements AdminProductAnalysisService {

  private final ProductMapper productMapper;
  private final OrderItemMapper orderItemMapper;
  private final CategoryMapper categoryMapper;
  private final FavoriteMapper favoriteMapper;

  @Override
  public Map<String, Object> overview() {
    // 总商品数
    Long totalProductCount = productMapper.selectCount(new LambdaQueryWrapper<>());
    // 上架商品数
    Long activeProductCount = productMapper.selectCount(
        new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getOnSale, true));
    // 总销量（通过所有 OrderItem 的 quantity 总和估算）
    List<OrderItemEntity> allItems = orderItemMapper.selectList(new LambdaQueryWrapper<>());
    int totalSales = allItems.stream().mapToInt(OrderItemEntity::getQuantity).sum();
    // 总收藏数
    Long totalFavorites = favoriteMapper.selectCount(new LambdaQueryWrapper<>());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalProductCount", totalProductCount);
    result.put("activeProductCount", activeProductCount);
    result.put("totalSales", totalSales);
    result.put("totalFavorites", totalFavorites);
    return result;
  }

  @Override
  public List<Map<String, Object>> topSales(int limit) {
    // 按销量排序查询商品
    Page<ProductEntity> page = productMapper.selectPage(
        new Page<>(1, limit),
        new LambdaQueryWrapper<ProductEntity>()
            .orderByDesc(ProductEntity::getSales));

    List<Map<String, Object>> result = new ArrayList<>();
    for (ProductEntity product : page.getRecords()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", product.getId());
      item.put("name", product.getName());
      item.put("sales", product.getSales());
      item.put("price", product.getPrice());
      item.put("stock", product.getStock());
      result.add(item);
    }
    return result;
  }

  @Override
  public List<Map<String, Object>> categoryDistribution() {
    // 查询所有分类及其商品数量
    List<CategoryEntity> categories = categoryMapper.selectList(new LambdaQueryWrapper<>());
    List<Map<String, Object>> result = new ArrayList<>();
    for (CategoryEntity category : categories) {
      Long count = productMapper.selectCount(
          new LambdaQueryWrapper<ProductEntity>().eq(ProductEntity::getCategoryId, category.getId()));
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("categoryId", category.getId());
      item.put("categoryName", category.getName());
      item.put("productCount", count);
      result.add(item);
    }
    return result;
  }

  @Override
  public List<Map<String, Object>> priceDistribution() {
    // 价格区间分布：0-50, 50-100, 100-200, 200-500, 500+
    String[][] ranges = {
        {"0-50", "0", "50"},
        {"50-100", "50", "100"},
        {"100-200", "100", "200"},
        {"200-500", "200", "500"},
        {"500+", "500", null}
    };

    List<Map<String, Object>> result = new ArrayList<>();
    for (String[] range : ranges) {
      LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<>();
      wrapper.ge(ProductEntity::getPrice, new BigDecimal(range[1]));
      if (range[2] != null) {
        wrapper.lt(ProductEntity::getPrice, new BigDecimal(range[2]));
      }
      Long count = productMapper.selectCount(wrapper);

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("range", range[0]);
      item.put("productCount", count);
      result.add(item);
    }
    return result;
  }
}

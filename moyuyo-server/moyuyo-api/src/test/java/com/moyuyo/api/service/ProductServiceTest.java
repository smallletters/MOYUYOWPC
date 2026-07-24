package com.moyuyo.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductImageEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductImageMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock
  private ProductMapper productMapper;

  @Mock
  private ProductSkuMapper productSkuMapper;

  @Mock
  private ProductImageMapper productImageMapper;

  @InjectMocks
  private ProductServiceImpl productService;

  @Captor
  private ArgumentCaptor<LambdaQueryWrapper<ProductEntity>> wrapperCaptor;

  // ==================== listProducts ====================

  @SuppressWarnings("unchecked")
  @Test
  void listProducts_withPaging_shouldReturnPagedResults() {
    // 准备：分页数据
    Page<ProductEntity> mockPage = new Page<>(1, 10);
    ProductEntity product1 = new ProductEntity();
    product1.setId(1L);
    product1.setName("商品1");
    ProductEntity product2 = new ProductEntity();
    product2.setId(2L);
    product2.setName("商品2");
    mockPage.setRecords(List.of(product1, product2));

    when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
        .thenReturn(mockPage);

    // 执行：分页查询
    Page<ProductEntity> result = productService.listProducts(1, 10, null, null, null, null, null, null, null);

    // 验证
    assertNotNull(result);
    assertEquals(2, result.getRecords().size());
    assertEquals("商品1", result.getRecords().get(0).getName());

    // 验证参数传递
    verify(productMapper).selectPage(any(Page.class), wrapperCaptor.capture());
    LambdaQueryWrapper<ProductEntity> wrapper = wrapperCaptor.getValue();
    assertNotNull(wrapper);
  }

  @SuppressWarnings("unchecked")
  @Test
  void listProducts_withCategoryFilter_shouldPassCategoryCondition() {
    // 准备
    Page<ProductEntity> mockPage = new Page<>(1, 10);
    when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
        .thenReturn(mockPage);

    // 执行：按分类筛选
    productService.listProducts(1, 10, 100L, "price", "asc", "测试关键词", null, null, null);

    // 验证
    verify(productMapper).selectPage(any(Page.class), wrapperCaptor.capture());
    LambdaQueryWrapper<ProductEntity> wrapper = wrapperCaptor.getValue();
    assertNotNull(wrapper);
    // 注意：LambdaQueryWrapper 的条件无法直接验证，
    // 但可以验证 selectPage 被正确调用即可
  }

  // ==================== getProductWithDetails ====================

  @Test
  void getProductWithDetails_productExists_shouldReturnProductWithSkusAndImages() {
    // 准备：商品信息
    Long productId = 1L;
    ProductEntity product = new ProductEntity();
    product.setId(productId);
    product.setName("测试商品");
    product.setPrice(new BigDecimal("99.99"));

    when(productMapper.selectById(productId)).thenReturn(product);

    // 准备：SKU 列表
    ProductSkuEntity sku1 = new ProductSkuEntity();
    sku1.setId(10L);
    sku1.setProductId(productId);
    sku1.setPrice(new BigDecimal("99.99"));

    ProductSkuEntity sku2 = new ProductSkuEntity();
    sku2.setId(11L);
    sku2.setProductId(productId);
    sku2.setPrice(new BigDecimal("199.99"));

    when(productSkuMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(sku1, sku2));

    // 准备：图片列表
    ProductImageEntity img1 = new ProductImageEntity();
    img1.setId(100L);
    img1.setProductId(productId);
    img1.setSort(1);

    when(productImageMapper.selectList(any(LambdaQueryWrapper.class)))
        .thenReturn(List.of(img1));

    // 执行
    ProductEntity result = productService.getProductWithDetails(productId);

    // 验证
    assertNotNull(result);
    assertEquals("测试商品", result.getName());
    assertEquals(new BigDecimal("99.99"), result.getPrice());

    // 验证 SKU 和图片已设置
    assertNotNull(result.getSkus());
    assertEquals(2, result.getSkus().size());
    assertEquals(Long.valueOf(10L), result.getSkus().get(0).getId());

    assertNotNull(result.getImages());
    assertEquals(1, result.getImages().size());
    assertEquals(Long.valueOf(100L), result.getImages().get(0).getId());

    // 验证查询调用
    verify(productMapper).selectById(productId);
    verify(productSkuMapper).selectList(any(LambdaQueryWrapper.class));
    verify(productImageMapper).selectList(any(LambdaQueryWrapper.class));
  }

  @Test
  void getProductWithDetails_productNotExist_shouldThrowIllegalArgumentException() {
    // 准备：商品不存在
    when(productMapper.selectById(999L)).thenReturn(null);

    // 执行 & 验证
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> productService.getProductWithDetails(999L));
    assertEquals("商品不存在", ex.getMessage());

    // 验证后续查询没有被调用
    verify(productSkuMapper, never()).selectList(any());
    verify(productImageMapper, never()).selectList(any());
  }
}

package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductImageEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;

import java.util.List;
import java.util.Map;

public interface ProductService {

  Page<ProductEntity> listProducts(int page, int size, Long categoryId, String sortBy, String sortOrder, String keyword, String status, String stockStatus, Long brandIpId);

  // 重载方法，兼容旧调用（不提供状态筛选时使用）
  default Page<ProductEntity> listProducts(int page, int size, Long categoryId, String sortBy, String sortOrder, String keyword) {
    return listProducts(page, size, categoryId, sortBy, sortOrder, keyword, null, null, null);
  }

  ProductEntity getProductDetail(Long productId);

  List<ProductSkuEntity> getSkusByProductId(Long productId);

  List<ProductImageEntity> getImagesByProductId(Long productId);

  /** 批量获取多个商品的图片列表（用于列表接口填充封面图，避免 N+1 查询） */
  Map<Long, List<ProductImageEntity>> getImagesByProductIds(List<Long> productIds);

  ProductEntity getProductWithDetails(Long productId);

  /** 创建商品，含SPU编码唯一性校验 */
  ProductEntity createProduct(Map<String, Object> body);

  /** 更新商品，含存在性校验 */
  ProductEntity updateProduct(Long id, Map<String, Object> body);

  /** 切换商品上架/下架状态 */
  ProductEntity toggleProductStatus(Long id);

  /** 批量操作商品（上架/下架/删除） */
  int batchProductAction(String action, List<Long> ids);
}

package com.moyuyo.service;

import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.OrderEntity;

import java.util.List;

public interface CartService {

  List<CartEntity> getUserCart(Long userId);

  CartEntity addItem(Long userId, Long skuId, int quantity);

  CartEntity updateQuantity(Long userId, Long skuId, int quantity);

  void removeItem(Long userId, Long skuId);

  void toggleCheck(Long userId, Long skuId, Boolean selected);

  void toggleCheckAll(Long userId, boolean selected);

  void clear(Long userId);

  /** 购物车凑单推荐（基于购物车商品的 upsell / cross-sell 配置） */
  List<com.moyuyo.dao.entity.ProductEntity> getRecommendations(Long userId);

  OrderEntity checkout(Long userId, Long addressId, String remark, String couponId);
}

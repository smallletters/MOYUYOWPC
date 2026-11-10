package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.cart.CartCheckoutRequest;
import com.moyuyo.common.dto.cart.CartRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.CartEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "购物车管理")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

  private final CartService cartService;

  @Operation(summary = "获取购物车")
  @GetMapping
  public Result<List<CartEntity>> getCart() {
    return Result.success(cartService.getUserCart(UserContextHolder.getUserId()));
  }

  @Operation(summary = "购物车凑单推荐")
  @GetMapping("/recommendations")
  public Result<List<com.moyuyo.dao.entity.ProductEntity>> getRecommendations() {
    return Result.success(cartService.getRecommendations(UserContextHolder.getUserId()));
  }

  @Operation(summary = "添加商品到购物车")
  @PostMapping
  public Result<CartEntity> addItem(@RequestBody CartRequest request) {
    return Result.success(cartService.addItem(UserContextHolder.getUserId(), request.getSkuId(), request.getQuantity()));
  }

  @Operation(summary = "更新商品数量")
  @PutMapping("/{skuId}")
  public Result<CartEntity> updateQuantity(
      @PathVariable Long skuId,
      @RequestBody CartRequest request) {
    return Result.success(cartService.updateQuantity(UserContextHolder.getUserId(), skuId, request.getQuantity()));
  }

  @Operation(summary = "删除购物车商品")
  @DeleteMapping("/{skuId}")
  public Result<Void> removeItem(@PathVariable Long skuId) {
    cartService.removeItem(UserContextHolder.getUserId(), skuId);
    return Result.success();
  }

  @Operation(summary = "勾选/取消勾选商品")
  @PutMapping("/check/{skuId}")
  public Result<Void> toggleCheck(@PathVariable Long skuId, @RequestBody CartRequest request) {
    cartService.toggleCheck(UserContextHolder.getUserId(), skuId, request.getSelected());
    return Result.success();
  }

  @Operation(summary = "全选/取消全选")
  @PutMapping("/check-all")
  public Result<Void> toggleCheckAll(@RequestBody CartRequest request) {
    cartService.toggleCheckAll(UserContextHolder.getUserId(), request.getSelected() != null && request.getSelected());
    return Result.success();
  }

  @Operation(summary = "清空购物车")
  @DeleteMapping
  public Result<Void> clear() {
    cartService.clear(UserContextHolder.getUserId());
    return Result.success();
  }

  @Operation(summary = "购物车结算")
  @PostMapping("/checkout")
  public Result<OrderEntity> checkout(@RequestBody CartCheckoutRequest request) {
    OrderEntity order = cartService.checkout(
        UserContextHolder.getUserId(),
        request.getAddressId(),
        request.getRemark(),
        request.getCouponId());
    return Result.success(order);
  }
}

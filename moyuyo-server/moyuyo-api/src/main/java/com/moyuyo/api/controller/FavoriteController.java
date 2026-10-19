package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.favorite.FavoriteRequest;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.FavoriteEntity;
import com.moyuyo.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收藏管理")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

  private final FavoriteService favoriteService;

  @Operation(summary = "收藏列表")
  @GetMapping
  public Result<List<FavoriteEntity>> getFavorites() {
    return Result.success(favoriteService.getUserFavorites(UserContextHolder.getUserId()));
  }

  @Operation(summary = "添加收藏")
  @PostMapping
  public Result<Void> addFavorite(@RequestBody FavoriteRequest request) {
    favoriteService.addFavorite(UserContextHolder.getUserId(), request.getProductId(), request.getSkuId(), request.getGroupId());
    return Result.success();
  }

  /**
   * 取消收藏。
   *
   * <p>注意：参数必须支持 query 方式。App 端 {@code api/cart.js#removeFavorite} 走的是
   * {@code DELETE /api/v1/favorites?productId=xx&skuId=yy}（刻意不用 DELETE + body，
   * 避免部分网关丢弃 body）。历史上这里只写了 {@code @RequestBody}，导致 query 里的
   * productId 完全没被读取，Service 拿到 productId=null 后拼出
   * {@code where user_id=? and product_id is null}，删 0 行却返回 200，
   * 前端提示"取消收藏成功"但刷新后记录仍在。
   *
   * <p>现在 query 优先，同时保留 {@code @RequestBody(required=false)} 兼容仍按 body
   * 传参的旧客户端。
   */
  @Operation(summary = "取消收藏")
  @DeleteMapping
  public Result<Void> removeFavorite(
      @RequestParam(value = "productId", required = false) Long productId,
      @RequestParam(value = "skuId", required = false) Long skuId,
      @RequestBody(required = false) FavoriteRequest body) {
    Long targetProductId = productId != null ? productId : (body != null ? body.getProductId() : null);
    Long targetSkuId = skuId != null ? skuId : (body != null ? body.getSkuId() : null);
    favoriteService.removeFavorite(UserContextHolder.getUserId(), targetProductId, targetSkuId);
    return Result.success();
  }
}

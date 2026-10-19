package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.FavoriteEntity;
import com.moyuyo.dao.mapper.FavoriteMapper;
import com.moyuyo.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
// 类级抑制：MyBatis-Plus 的 LambdaQueryWrapper.eq(FavoriteEntity::getXxx, ...) 等方法引用
// 在 Eclipse JDT 类型推导下，函数描述符是 Function<FavoriteEntity, Object>，与 lombok @Data 生成的
// @Nonnull FavoriteEntity 参数不严格匹配 → 报 67109822 (severity 4, info 级)；
// javac 编译时无此告警，运行时也无影响，故统一在类级别抑制，避免逐行加注解。
@SuppressWarnings("null")
public class FavoriteServiceImpl implements FavoriteService {

  private final FavoriteMapper favoriteMapper;

  @Override
  public List<FavoriteEntity> getUserFavorites(Long userId) {
    return favoriteMapper.selectList(
        new LambdaQueryWrapper<FavoriteEntity>()
            .eq(FavoriteEntity::getUserId, userId)
            .orderByDesc(FavoriteEntity::getCreateTime));
  }

  @Override
  @Transactional
  public void addFavorite(Long userId, Long productId, Long skuId, Long groupId) {
    Long count = favoriteMapper.selectCount(
        new LambdaQueryWrapper<FavoriteEntity>()
            .eq(FavoriteEntity::getUserId, userId)
            .eq(FavoriteEntity::getProductId, productId));

    if (count > 0) {
      throw new IllegalArgumentException("该商品已收藏");
    }

    FavoriteEntity favorite = new FavoriteEntity();
    favorite.setUserId(userId);
    favorite.setProductId(productId);
    favorite.setGroupId(groupId);
    favoriteMapper.insert(favorite);

    log.info("Favorite added: userId={}, productId={}", userId, productId);
  }

  @Override
  @Transactional
  public void removeFavorite(Long userId, Long productId, Long skuId) {
    // 防御：productId 为空时 LambdaQueryWrapper 会拼出 "product_id = null"（恒不成立），
    // 删 0 行却静默返回成功 —— 前端会提示"取消收藏成功"但数据仍在。
    // 参数缺失必须显式失败，不能当作"删了个寂寞"。
    if (productId == null) {
      throw new IllegalArgumentException("缺少商品ID，无法取消收藏");
    }
    int deleted = favoriteMapper.delete(
        new LambdaQueryWrapper<FavoriteEntity>()
            .eq(FavoriteEntity::getUserId, userId)
            .eq(FavoriteEntity::getProductId, productId));

    if (deleted == 0) {
      // 幂等语义：记录本就不存在（例如另一端已取消）不算错误，但要留痕便于排查
      log.warn("Favorite remove matched 0 rows: userId={}, productId={}", userId, productId);
    } else {
      log.info("Favorite removed: userId={}, productId={}, rows={}", userId, productId, deleted);
    }
  }
}

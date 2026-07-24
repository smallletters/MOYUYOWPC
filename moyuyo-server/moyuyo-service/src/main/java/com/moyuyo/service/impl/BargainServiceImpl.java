package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.entity.BargainEntity;
import com.moyuyo.dao.entity.BargainHelpEntity;
import com.moyuyo.dao.mapper.BargainHelpMapper;
import com.moyuyo.dao.mapper.BargainMapper;
import com.moyuyo.service.BargainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BargainServiceImpl implements BargainService {

  private final BargainMapper bargainMapper;
  private final BargainHelpMapper bargainHelpMapper;

  @Override
  public List<BargainEntity> list() {
    return bargainMapper.selectList(
        new LambdaQueryWrapper<BargainEntity>()
            .eq(BargainEntity::getStatus, "ACTIVE")
            .orderByDesc(BargainEntity::getCreateTime));
  }

  @Override
  public BargainEntity getDetail(Long id) {
    BargainEntity entity = bargainMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("砍价活动不存在");
    }
    return entity;
  }

  @Override
  @Transactional
  public BargainHelpEntity help(Long id, Long userId) {
    BargainEntity bargain = bargainMapper.selectById(id);
    if (bargain == null || !"ACTIVE".equals(bargain.getStatus())) {
      throw new IllegalArgumentException("砍价活动不存在或已结束");
    }
    long helped = bargainHelpMapper.selectCount(
        new LambdaQueryWrapper<BargainHelpEntity>()
            .eq(BargainHelpEntity::getBargainId, id)
            .eq(BargainHelpEntity::getUserId, userId));
    if (helped > 0) {
      throw new IllegalArgumentException("您已参与过该砍价活动");
    }

    // 使用原子更新防止并发超卖：WHERE remaining_stock > 0
    LambdaUpdateWrapper<BargainEntity> updateWrapper = new LambdaUpdateWrapper<>();
    updateWrapper.eq(BargainEntity::getId, id)
        .setSql("remaining_stock = remaining_stock - 1")
        .gt(BargainEntity::getRemainingStock, 0);
    int rows = bargainMapper.update(null, updateWrapper);
    if (rows == 0) {
      throw new IllegalStateException("库存不足，砍价失败");
    }

    BigDecimal remaining = bargain.getOriginalPrice().subtract(bargain.getBargainPrice());
    BargainHelpEntity help = new BargainHelpEntity();
    help.setBargainId(id);
    help.setUserId(userId);
    help.setHelpAmount(remaining.divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP));
    bargainHelpMapper.insert(help);
    log.info("Bargain help: bargainId={}, userId={}, amount={}", id, userId, help.getHelpAmount());
    return help;
  }

  @Override
  public List<BargainHelpEntity> getHelpers(Long id) {
    return bargainHelpMapper.selectList(
        new LambdaQueryWrapper<BargainHelpEntity>()
            .eq(BargainHelpEntity::getBargainId, id)
            .orderByDesc(BargainHelpEntity::getCreateTime));
  }
}

package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FlashSaleEntity;
import com.moyuyo.dao.entity.FlashSaleOrderEntity;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.mapper.FlashSaleMapper;
import com.moyuyo.dao.mapper.FlashSaleOrderMapper;
import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleOrderMapper flashSaleOrderMapper;
    private final ProductMapper productMapper;

    @Override
    public Page<FlashSaleEntity> listActive(int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        Page<FlashSaleEntity> result = flashSaleMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<FlashSaleEntity>()
                        .eq(FlashSaleEntity::getActive, true)
                        .le(FlashSaleEntity::getStartTime, now)
                        .ge(FlashSaleEntity::getEndTime, now)
                        .orderByAsc(FlashSaleEntity::getEndTime));
        // 活动时间窗口内但商品已被下架/删除的秒杀不再露出
        // 注：只裁剪当前页 records，不动 total（否则会把未过滤页算进去导致全局总数失真）
        if (result != null && result.getRecords() != null && !result.getRecords().isEmpty()) {
            List<FlashSaleEntity> kept = new ArrayList<>();
            for (FlashSaleEntity f : result.getRecords()) {
                ProductEntity p = productMapper.selectById(f.getProductId());
                if (p != null && Boolean.TRUE.equals(p.getOnSale())) {
                    kept.add(f);
                }
            }
            result.setRecords(kept);
        }
        return result;
    }

    @Override
    public FlashSaleEntity getFlashSaleDetail(Long id) {
        FlashSaleEntity entity = flashSaleMapper.selectById(id);
        if (entity == null) throw new IllegalArgumentException("活动不存在");
        return entity;
    }

    @Override
    @Transactional
    public void placeFlashOrder(Long userId, Long flashSaleId, Integer quantity) {
        FlashSaleEntity flash = flashSaleMapper.selectById(flashSaleId);
        if (flash == null) throw new IllegalArgumentException("活动不存在");
        if (!flash.getActive()) throw new IllegalStateException("活动已结束");

        // 商品已下架/已删除时禁止抢购
        ProductEntity product = productMapper.selectById(flash.getProductId());
        if (product == null || !Boolean.TRUE.equals(product.getOnSale())) {
            throw new IllegalStateException("商品已下架");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(flash.getStartTime())) throw new IllegalStateException("活动尚未开始");
        if (now.isAfter(flash.getEndTime())) throw new IllegalStateException("活动已结束");

        if (flash.getLimitPerUser() != null && flash.getLimitPerUser() > 0) {
            long userBought = flashSaleOrderMapper.selectCount(
                    new LambdaQueryWrapper<FlashSaleOrderEntity>()
                            .eq(FlashSaleOrderEntity::getFlashSaleId, flashSaleId)
                            .eq(FlashSaleOrderEntity::getUserId, userId));
            if (userBought + quantity > flash.getLimitPerUser()) {
                throw new IllegalStateException("每人限购 " + flash.getLimitPerUser() + " 件");
            }
        }

        // 使用原子更新防止并发超卖：WHERE sold_stock + quantity <= total_stock
        LambdaUpdateWrapper<FlashSaleEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(FlashSaleEntity::getId, flashSaleId)
                .setSql("sold_stock = COALESCE(sold_stock, 0) + " + quantity)
                .apply("COALESCE(sold_stock, 0) + {0} <= total_stock", quantity);
        int rows = flashSaleMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw new IllegalStateException("库存不足，抢购失败");
        }

        FlashSaleOrderEntity order = new FlashSaleOrderEntity();
        order.setFlashSaleId(flashSaleId);
        order.setUserId(userId);
        order.setQuantity(quantity);
        flashSaleOrderMapper.insert(order);

        log.info("Flash order placed: flashSaleId={}, userId={}, qty={}", flashSaleId, userId, quantity);
    }
}

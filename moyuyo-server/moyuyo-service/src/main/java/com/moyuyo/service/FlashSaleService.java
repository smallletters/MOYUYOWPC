package com.moyuyo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.FlashSaleEntity;

public interface FlashSaleService {

    /**
     * 分页查询秒杀活动。
     *
     * @param session 场次：ongoing（进行中）/ upcoming（即将开始）/ ended（已结束），为空时默认进行中
     */
    Page<FlashSaleEntity> listActive(int page, int size, String session);

    FlashSaleEntity getFlashSaleDetail(Long id);

    void placeFlashOrder(Long userId, Long flashSaleId, Integer quantity);
}

package com.moyuyo.service;

import com.moyuyo.dao.entity.LogisticsEntity;

public interface LogisticsService {

    LogisticsEntity shipOrder(Long orderId, String carrier, String trackingNumber);

    LogisticsEntity getLogisticsByOrderId(Long orderId);

    LogisticsEntity updateTracking(Long logisticsId, String traces);

    /**
     * 确认收货（条件更新，幂等）
     *
     * @return true=订单本次确实流转为已收货；false=订单不存在/非已发货状态，未发生状态变更
     */
    boolean confirmReceived(Long orderId);
}

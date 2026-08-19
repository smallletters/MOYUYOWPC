package com.moyuyo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.common.dto.exchange.ExchangeApplyRequest;
import com.moyuyo.common.dto.exchange.ExchangeVO;

/**
 * 换货单服务
 * 状态机：APPLIED → APPROVED → SHIPPED_BACK → RESHIPPED → COMPLETED
 */
public interface ExchangeService {

    /** 用户申请换货（订单状态机进入 EXCHANGING） */
    ExchangeVO applyExchange(Long userId, ExchangeApplyRequest request);

    /** 管理员审核通过换货 */
    void approveExchange(Long exchangeId, Long operatorId);

    /** 管理员拒绝换货 */
    void rejectExchange(Long exchangeId, Long operatorId, String reason);

    /** 用户回寄后录入回寄物流（APPROVED → SHIPPED_BACK） */
    void fillReturnShipping(Long exchangeId, String carrier, String trackingNo);

    /** 仓库重新发货录入新货物流（SHIPPED_BACK → RESHIPPED） */
    void reship(Long exchangeId, String carrier, String trackingNo, Long operatorId);

    /** 确认完成换货（RESHIPPED → COMPLETED，订单进入 EXCHANGED） */
    void completeExchange(Long exchangeId, Long operatorId);

    /** 取消换货（任意阶段 → CANCELLED，订单恢复原状态） */
    void cancelExchange(Long exchangeId, Long operatorId, String reason);

    /** 换货详情 */
    ExchangeVO getExchangeDetail(Long exchangeId);

    /** 管理员分页查询换货列表 */
    IPage<ExchangeVO> listAll(int page, int size, String status);

    /** 用户分页查询我的换货单 */
    IPage<ExchangeVO> listUserExchanges(Long userId, int page, int size);
}

package com.moyuyo.service;

import com.moyuyo.common.enums.RefundChannelEnum;

import java.math.BigDecimal;

/**
 * 第三方支付渠道退款服务
 * 抽象不同支付渠道的退款调用，支持后续扩展
 */
public interface RefundChannelService {

    /**
     * 调用第三方支付平台发起退款
     *
     * @param channel        支付渠道（WECHAT / ALIPAY / UNIONPAY / STRIPE / PAYPAL）
     * @param payTransactionId 原支付交易号（创建支付时返回）
     * @param orderNo         订单号（用于退款单号拼接与对账）
     * @param refundAmount    退款金额
     * @param refundNo        退款单号（用于幂等与对账）
     * @return 第三方返回的退款流水号（用于落库 mo_refund.transaction_id）
     */
    String refund(RefundChannelEnum channel, String payTransactionId, String orderNo,
                  BigDecimal refundAmount, String refundNo);
}

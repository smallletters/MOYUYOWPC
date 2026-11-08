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
     * @param currency        订单币种（ISO 4217，如 USD/EUR/CAD；PayPal 等渠道退款需要）
     * @return 第三方返回的退款流水号（用于落库 mo_refund.transaction_id）
     */
    String refund(RefundChannelEnum channel, String payTransactionId, String orderNo,
                  BigDecimal refundAmount, String refundNo, String currency);

    /**
     * 判断该渠道是否已配置真实退款能力。
     * <p>
     * 未配置密钥时 refund() 会降级为本地生成流水号（仅用于开发联调），
     * 管理后台的"自动原路退回"需要依赖此方法拦截，避免静默假退款。
     *
     * @param channel 支付渠道
     * @return true=已配置真实渠道密钥，可直接调用第三方退款
     */
    boolean isAutoRefundSupported(RefundChannelEnum channel);
}

package com.moyuyo.common.enums;

import java.util.Arrays;

/**
 * 退款支付渠道枚举
 * 注：V1.1.2 起，订单支付渠道已统一为 STRIPE/PAYPAL（兼容旧值 WECHAT/ALIPAY）
 * 退款渠道保持与支付渠道对齐，便于对账与回溯
 */
public enum RefundChannelEnum {
    STRIPE("Stripe", "stripe"),
    PAYPAL("PayPal", "paypal"),
    WECHAT("微信支付", "wechat"),
    ALIPAY("支付宝", "alipay"),
    UNIONPAY("银联", "unionpay"),
    WALLET("钱包余额", "wallet"),
    POINTS("积分", "points"),
    OTHER("其他", "other");

    private final String displayName;
    private final String code;

    RefundChannelEnum(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    /** 从字符串解析（兼容旧值） */
    public static RefundChannelEnum fromValue(String value) {
        if (value == null) return OTHER;
        String normalized = value.toUpperCase().replace("-", "_");
        for (RefundChannelEnum e : values()) {
            if (e.name().equals(normalized) || e.code.equalsIgnoreCase(value)) {
                return e;
            }
        }
        // 兼容 WECHAT_PAY / WECHATPAY 等变体
        if (normalized.contains("WECHAT")) return WECHAT;
        if (normalized.contains("ALIPAY") || normalized.contains("ALI_PAY")) return ALIPAY;
        if (normalized.contains("UNION") || normalized.contains("UPI")) return UNIONPAY;
        return OTHER;
    }

    /** 枚举的所有 code（用于 @Value 等场景校验） */
    public static boolean isSupported(String value) {
        return Arrays.stream(values()).anyMatch(e -> e.name().equalsIgnoreCase(value));
    }
}

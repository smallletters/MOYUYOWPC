package com.moyuyo.common.mq;

/**
 * RocketMQ Topic 和消费组常量定义
 */
public class TopicConstant {

    // ==================== Topic ====================
    /** 订单创建 */
    public static final String ORDER_CREATED = "order_created";
    /** 订单支付成功 */
    public static final String ORDER_PAID = "order_paid";
    /** 订单取消 */
    public static final String ORDER_CANCELLED = "order_cancelled";
    /** 支付回调 */
    public static final String PAYMENT_CALLBACK = "payment_callback";
    /** 库存变更 */
    public static final String INVENTORY_CHANGE = "inventory_change";
    /** 秒杀订单 */
    public static final String SECKILL_ORDER = "seckill_order";
    /** WooCommerce 同步 */
    public static final String WOO_SYNC = "woo_sync";
    /** 用户注册 */
    public static final String USER_REGISTER = "user_register";
    /** 通知发送 */
    public static final String NOTIFICATION_SEND = "notification_send";

    // ==================== 消费组 ====================
    /** 订单相关消费组 */
    public static final String CONSUMER_GROUP_ORDER = "order-consumer-group";
    /** WooCommerce 同步消费组 */
    public static final String CONSUMER_GROUP_WOO = "woo-consumer-group";
    /** 通知发送消费组 */
    public static final String CONSUMER_GROUP_NOTIFICATION = "notification-consumer-group";

    private TopicConstant() {
        // 工具类禁止实例化
    }
}

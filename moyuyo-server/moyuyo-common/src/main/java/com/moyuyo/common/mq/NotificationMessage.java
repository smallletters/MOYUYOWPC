package com.moyuyo.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知消息体（业务模块 -> MQ -> 通知消费者）
 * <p>
 * 字段说明：
 * - userId：接收通知的用户
 * - type：通知分类，与 mo_notification.type 对应（ORDER / ACTIVITY / SYSTEM）
 * - title：通知标题
 * - content：通知正文
 * - relatedId：关联业务 ID（如订单号、活动 ID），可为 null
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    /** 接收用户 ID */
    private Long userId;

    /** 通知类型：ORDER / ACTIVITY / SYSTEM */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联业务 ID，可为空 */
    private Long relatedId;
}
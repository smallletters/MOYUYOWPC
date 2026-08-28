package com.moyuyo.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.dao.entity.NotificationEntity;

public interface NotificationService {

  IPage<NotificationEntity> listNotifications(Long userId, int page, int size);

  NotificationEntity getNotificationDetail(Long id, Long userId);

  void markAsRead(Long id, Long userId);

  void markAllAsRead(Long userId);

  void deleteNotification(Long id, Long userId);

  /**
   * 直接落库一条通知（同步场景使用；异步场景由 MQ 消费者调用）
   */
  NotificationEntity saveNotification(Long userId, String type, String title, String content, Long relatedId);
}

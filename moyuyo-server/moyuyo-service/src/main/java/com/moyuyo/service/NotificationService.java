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
   * 批量删除通知
   *
   * @param ids  为空时删除该用户全部（或指定分类的）通知；非空时只删除这些 id
   * @param type 为空表示不限分类；非空只删除该分类（忽略大小写，如 order/system/activity）
   * @return 实际删除条数
   */
  int deleteNotifications(Long userId, java.util.List<Long> ids, String type);

  /**
   * 直接落库一条通知（同步场景使用；异步场景由 MQ 消费者调用）
   */
  NotificationEntity saveNotification(Long userId, String type, String title, String content, Long relatedId);

  /**
   * 直接落库一条通知（带业务类型）
   *
   * @param bizType 业务类型 ORDER / AFTER_SALE，供 APP 决定点击跳转目标
   */
  NotificationEntity saveNotification(Long userId, String type, String bizType, String title,
                                      String content, Long relatedId);

  /**
   * 业务事件通知用户：写站内信 + 尽力而为投递 App 推送（FCM/APNs）。
   * <p>
   * 推送失败只记日志，不影响站内信落库与主业务事务；调用方无需处理异常。
   *
   * @param type    通知分类 ORDER / ACTIVITY / SYSTEM，对应 APP 通知中心 tab
   * @param bizType 业务类型 ORDER / AFTER_SALE，APP 点击跳转依据
   */
  void notifyUser(Long userId, String type, String bizType, String title, String content, Long relatedId);
}

package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.NotificationEntity;
import com.moyuyo.dao.mapper.NotificationMapper;
import com.moyuyo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

  private final NotificationMapper notificationMapper;

  @Override
  public IPage<NotificationEntity> listNotifications(Long userId, int page, int size) {
    return notificationMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<NotificationEntity>()
            .eq(NotificationEntity::getUserId, userId)
            .orderByDesc(NotificationEntity::getCreateTime));
  }

  @Override
  public NotificationEntity getNotificationDetail(Long id, Long userId) {
    NotificationEntity entity = notificationMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("通知不存在");
    }
    if (!Objects.equals(entity.getUserId(), userId)) {
      throw new IllegalArgumentException("无权访问该通知");
    }
    return entity;
  }

  @Override
  @Transactional
  public void markAsRead(Long id, Long userId) {
    NotificationEntity entity = getNotificationDetail(id, userId);
    entity.setRead(1);
    notificationMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void markAllAsRead(Long userId) {
    notificationMapper.update(null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<NotificationEntity>()
            .eq(NotificationEntity::getUserId, userId)
            .eq(NotificationEntity::getRead, 0)
            .set(NotificationEntity::getRead, 1));
  }

  @Override
  @Transactional
  public void deleteNotification(Long id, Long userId) {
    NotificationEntity entity = getNotificationDetail(id, userId);
    notificationMapper.deleteById(entity.getId());
  }

  @Override
  public NotificationEntity saveNotification(Long userId, String type, String title, String content, Long relatedId) {
    // 构造通知实体，createTime 由 MetaObjectHandler 自动填充
    NotificationEntity entity = new NotificationEntity();
    entity.setUserId(userId);
    entity.setType(type);
    entity.setTitle(title);
    entity.setContent(content);
    entity.setRelatedId(relatedId);
    entity.setRead(0);
    notificationMapper.insert(entity);
    log.info("通知落库成功: userId={}, type={}, title={}", userId, type, title);
    return entity;
  }
}

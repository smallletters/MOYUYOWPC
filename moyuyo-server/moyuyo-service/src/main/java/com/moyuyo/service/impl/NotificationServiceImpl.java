package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.AfterSalesEntity;
import com.moyuyo.dao.entity.NotificationEntity;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.mapper.AfterSalesMapper;
import com.moyuyo.dao.mapper.NotificationMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

  private final NotificationMapper notificationMapper;
  /** 用于兼容历史通知：按 relatedId 判断是订单还是售后 */
  private final OrderMapper orderMapper;
  private final AfterSalesMapper afterSalesMapper;
  /** 异步推送执行器（独立 Bean 才能让 @Async 生效） */
  private final NotificationPushDispatcher pushDispatcher;

  @Override
  public IPage<NotificationEntity> listNotifications(Long userId, int page, int size) {
    IPage<NotificationEntity> result = notificationMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<NotificationEntity>()
            .eq(NotificationEntity::getUserId, userId)
            .orderByDesc(NotificationEntity::getCreateTime));
    fillLegacyBizType(result.getRecords());
    return result;
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
    if (entity.getBizType() == null) {
      fillLegacyBizType(List.of(entity));
    }
    return entity;
  }

  /**
   * 历史通知兼容：早期数据没有 biz_type，按 relatedId 判断归属。
   * <p>
   * 只在返回对象上补齐（不回写数据库），避免读接口产生写操作；
   * 先判售后再判订单，与回填 SQL 的口径保持一致。
   */
  private void fillLegacyBizType(List<NotificationEntity> records) {
    if (records == null || records.isEmpty()) {
      return;
    }
    Set<Long> candidateIds = new HashSet<>();
    for (NotificationEntity record : records) {
      if ("ORDER".equalsIgnoreCase(record.getType())
          && record.getBizType() == null
          && record.getRelatedId() != null) {
        candidateIds.add(record.getRelatedId());
      }
    }
    if (candidateIds.isEmpty()) {
      return;
    }
    Set<Long> orderIds = new HashSet<>();
    orderMapper.selectBatchIds(candidateIds)
        .forEach(order -> orderIds.add(order.getId()));
    Set<Long> afterSaleIds = new HashSet<>();
    afterSalesMapper.selectBatchIds(candidateIds)
        .forEach(item -> afterSaleIds.add(item.getId()));

    for (NotificationEntity record : records) {
      if (!"ORDER".equalsIgnoreCase(record.getType())
          || record.getBizType() != null
          || record.getRelatedId() == null) {
        continue;
      }
      if (afterSaleIds.contains(record.getRelatedId())) {
        record.setBizType("AFTER_SALE");
      } else if (orderIds.contains(record.getRelatedId())) {
        record.setBizType("ORDER");
      }
    }
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
  @Transactional
  public int deleteNotifications(Long userId, List<Long> ids, String type) {
    if (userId == null) {
      return 0;
    }
    LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<NotificationEntity>()
        .eq(NotificationEntity::getUserId, userId);
    // ids 非空时按 id 批量删除；为空表示清空该用户（指定分类的）全部通知
    if (ids != null && !ids.isEmpty()) {
      wrapper.in(NotificationEntity::getId, ids);
    }
    // 分类过滤：历史数据既有大写 ORDER 也有小写 system，统一转大写比较
    if (type != null && !type.isBlank()) {
      wrapper.apply("UPPER(type) = {0}", type.trim().toUpperCase());
    }
    int deleted = notificationMapper.delete(wrapper);
    log.info("批量删除通知: userId={}, ids={}, type={}, deleted={}",
        userId, ids == null ? "ALL" : ids.size(), type == null ? "ALL" : type, deleted);
    return deleted;
  }

  @Override
  public NotificationEntity saveNotification(Long userId, String type, String title, String content, Long relatedId) {
    return saveNotification(userId, type, null, title, content, relatedId);
  }

  @Override
  public NotificationEntity saveNotification(Long userId, String type, String bizType, String title,
                                             String content, Long relatedId) {
    // 构造通知实体，createTime 由 MetaObjectHandler 自动填充
    NotificationEntity entity = new NotificationEntity();
    entity.setUserId(userId);
    entity.setType(type);
    entity.setBizType(bizType);
    entity.setTitle(title);
    entity.setContent(content);
    entity.setRelatedId(relatedId);
    entity.setRead(0);
    notificationMapper.insert(entity);
    log.info("通知落库成功: userId={}, type={}, bizType={}, title={}", userId, type, bizType, title);
    return entity;
  }

  @Override
  public void notifyUser(Long userId, String type, String bizType, String title, String content,
                         Long relatedId) {
    if (userId == null) {
      return;
    }
    // 通知属于旁路副作用：站内信与推送都尽力而为，任何异常只记日志，
    // 绝不能让业务主流程（售后/订单等）因通知失败而回滚
    try {
      saveNotification(userId, type, bizType, title, content, relatedId);
    } catch (Exception e) {
      log.warn("[notify] 站内信落库失败: userId={}, reason={}", userId, e.getMessage());
      return;
    }
    // 推送是网络 I/O：放到事务提交后执行，避免长时间占用业务事务与数据库连接
    pushAfterCommit(userId, title, content);
  }

  /**
   * 事务内调用时注册 afterCommit 回调；无事务时直接执行。
   * <p>
   * 推送交给 {@link NotificationPushDispatcher} 异步执行：
   * 既避免占用业务事务与数据库连接，也避免批量场景（批量发货/超时关闭）逐条同步 HTTP 拖慢请求。
   */
  private void pushAfterCommit(Long userId, String title, String content) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          pushDispatcher.dispatch(userId, title, content);
        }
      });
      return;
    }
    pushDispatcher.dispatch(userId, title, content);
  }
}

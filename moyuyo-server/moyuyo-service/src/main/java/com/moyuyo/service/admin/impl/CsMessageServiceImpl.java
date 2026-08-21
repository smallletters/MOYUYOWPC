package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.dao.admin.mapper.CsMessageMapper;
import com.moyuyo.dao.admin.mapper.CsSessionMapper;
import com.moyuyo.service.admin.CsMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客服消息服务实现：
 * - 写入消息 → 同步更新会话的 message_count / last_message_at
 * - 关闭会话：写 close_time + status='CLOSED'
 * - 转接会话：替换 operator_id + 写一条 SYSTEM 提示消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsMessageServiceImpl implements CsMessageService {

  private final CsMessageMapper csMessageMapper;
  private final CsSessionMapper csSessionMapper;

  @Override
  public List<CsMessageEntity> listMessages(Long sessionId) {
    if (sessionId == null) return List.of();
    return csMessageMapper.selectList(
        new LambdaQueryWrapper<CsMessageEntity>()
            .eq(CsMessageEntity::getSessionId, sessionId)
            .orderByAsc(CsMessageEntity::getCreateTime)
    );
  }

  @Override
  public List<CsMessageEntity> pollMessages(Long sessionId, LocalDateTime since) {
    if (sessionId == null) return List.of();
    LambdaQueryWrapper<CsMessageEntity> qw = new LambdaQueryWrapper<CsMessageEntity>()
        .eq(CsMessageEntity::getSessionId, sessionId);
    if (since != null) {
      // 只取 create_time > since 的新消息（半开区间，让 since 时刻的消息下次轮询还能拿到，避免丢消息）
      qw.gt(CsMessageEntity::getCreateTime, since);
    }
    return csMessageMapper.selectList(qw.orderByAsc(CsMessageEntity::getCreateTime));
  }

  @Override
  @Transactional
  public CsMessageEntity sendMessage(CsMessageEntity message) {
    if (message == null || message.getSessionId() == null) {
      throw new IllegalArgumentException("消息缺少 sessionId");
    }
    if (message.getContent() == null || message.getContent().isBlank()) {
      throw new IllegalArgumentException("消息内容不能为空");
    }
    // 默认值
    if (message.getContentType() == null) message.setContentType("TEXT");
    if (message.getReadFlag() == null) {
      // 客服发的消息无需标记已读；用户发的消息初始为未读
      message.setReadFlag("AGENT".equals(message.getSenderType()) ? 1 : 0);
    }
    if (message.getCreateTime() == null) message.setCreateTime(LocalDateTime.now());

    // 同步更新会话前先校验状态：CLOSED 会话不能再发消息
    CsSessionEntity session = csSessionMapper.selectById(message.getSessionId());
    if (session == null) {
      throw new IllegalArgumentException("会话不存在: " + message.getSessionId());
    }
    if ("CLOSED".equals(session.getStatus())) {
      throw new IllegalStateException("会话已关闭，无法发送新消息");
    }

    csMessageMapper.insert(message);

    // 同步更新会话：message_count++、last_message_at = now
    int newCount = (session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1;
    session.setMessageCount(newCount);
    session.setLastMessageAt(message.getCreateTime());
    csSessionMapper.updateById(session);
    return message;
  }

  @Override
  @Transactional
  public int markRead(Long sessionId) {
    if (sessionId == null) return 0;
    // 用 wrapper 而非逐行：兼容性更强
    var msgs = csMessageMapper.selectList(
        new LambdaQueryWrapper<CsMessageEntity>()
            .eq(CsMessageEntity::getSessionId, sessionId)
            .eq(CsMessageEntity::getSenderType, "USER")
            .eq(CsMessageEntity::getReadFlag, 0)
    );
    int n = 0;
    for (CsMessageEntity m : msgs) {
      m.setReadFlag(1);
      csMessageMapper.updateById(m);
      n++;
    }
    return n;
  }

  @Override
  @Transactional
  public CsSessionEntity closeSession(Long sessionId) {
    CsSessionEntity s = csSessionMapper.selectById(sessionId);
    if (s == null) throw new IllegalArgumentException("会话不存在: " + sessionId);
    s.setStatus("CLOSED");
    if (s.getCloseTime() == null) s.setCloseTime(LocalDateTime.now());
    csSessionMapper.updateById(s);
    return s;
  }

  @Override
  @Transactional
  public CsSessionEntity transferSession(Long sessionId, Long newOperatorId, String operatorName) {
    CsSessionEntity s = csSessionMapper.selectById(sessionId);
    if (s == null) throw new IllegalArgumentException("会话不存在: " + sessionId);
    // 关闭中的会话不允许转接
    if ("CLOSED".equals(s.getStatus())) {
      throw new IllegalStateException("会话已关闭，无法转接");
    }
    s.setCsStaffId(newOperatorId);
    // 乐观锁：先读旧 msgCount，转接前再 UPDATE WHERE id=? AND message_count=? 防止并发覆盖
    Integer oldCount = s.getMessageCount();
    s.setMessageCount(oldCount == null ? 0 : oldCount + 1);
    s.setLastMessageAt(LocalDateTime.now());
    int updated = csMessageService_updateByIdOptimistic(s, oldCount);
    if (updated == 0) {
      // 并发冲突：提示调用方重试
      throw new IllegalStateException("会话状态已变更，请刷新后重试");
    }
    // 同时写一条 SYSTEM 提示消息，便于聊天记录呈现转接上下文
    CsMessageEntity sys = new CsMessageEntity();
    sys.setSessionId(sessionId);
    sys.setSenderType("SYSTEM");
    sys.setContentType("SYSTEM");
    sys.setReadFlag(1);
    sys.setSenderName("系统");
    sys.setContent("会话已转接给客服 " + (operatorName == null ? ("#" + newOperatorId) : operatorName));
    sys.setCreateTime(LocalDateTime.now());
    csMessageMapper.insert(sys);
    return s;
  }

  /**
   * 乐观锁更新：只在 message_count 与旧值一致时才更新。
   * 用 MyBatis-Plus 的 LambdaUpdate + 手动条件实现，避免引入新依赖。
   */
  private int csMessageService_updateByIdOptimistic(CsSessionEntity s, Integer expectedCount) {
    com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CsSessionEntity> uw =
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CsSessionEntity>()
            .eq(CsSessionEntity::getId, s.getId())
            .eq(CsSessionEntity::getMessageCount, expectedCount)
            .set(CsSessionEntity::getCsStaffId, s.getCsStaffId())
            .set(CsSessionEntity::getMessageCount, s.getMessageCount())
            .set(CsSessionEntity::getLastMessageAt, s.getLastMessageAt());
    return csSessionMapper.update(null, uw);
  }
}

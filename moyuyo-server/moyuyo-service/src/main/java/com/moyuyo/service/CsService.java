package com.moyuyo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.dao.admin.mapper.CsMessageMapper;
import com.moyuyo.dao.admin.mapper.CsSessionMapper;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.ai.AiCsReplyService;
import com.moyuyo.service.config.AiCsConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 客服会话 + 消息服务。 */
@Service
@RequiredArgsConstructor
// 抑制 JDT 对 MyBatis-Plus Lambda 方法引用的 @Nonnull 空值分析误报
@SuppressWarnings("null")
public class CsService {

  private final CsSessionMapper sessionMapper;
  private final CsMessageMapper messageMapper;
  private final UserMapper userMapper;
  private final CsChatContextService csChatContextService;
  private final AiCsConfigService aiCsConfigService;
  private final AiCsReplyService aiCsReplyService;

  public Page<CsSessionEntity> listUserSessions(Long userId, int page, int size) {
    return sessionMapper.selectPage(new Page<>(page, size),
        new LambdaQueryWrapper<CsSessionEntity>()
            .eq(CsSessionEntity::getUserId, userId)
            .orderByDesc(CsSessionEntity::getLastMessageAt));
  }

  public List<CsMessageEntity> listMessages(Long sessionId, Long currentUserId) {
    // 把客服/系统消息标记为已读
    messageMapper.update(null,
        new LambdaUpdateWrapper<CsMessageEntity>()
            .eq(CsMessageEntity::getSessionId, sessionId)
            .ne(CsMessageEntity::getSenderType, "USER")
            .set(CsMessageEntity::getReadFlag, 1));
    return messageMapper.selectList(
        new LambdaQueryWrapper<CsMessageEntity>()
            .eq(CsMessageEntity::getSessionId, sessionId)
            .orderByAsc(CsMessageEntity::getCreateTime));
  }

  @Transactional
  public CsSessionEntity createSession(Long userId, String category,
                                       Long productId, String productName, String orderNo) {
    CsSessionEntity exist = sessionMapper.selectOne(
        new LambdaQueryWrapper<CsSessionEntity>()
            .eq(CsSessionEntity::getUserId, userId)
            .eq(CsSessionEntity::getStatus, "WAITING")
            .orderByDesc(CsSessionEntity::getCreateTime)
            .last("LIMIT 1"));
    if (exist != null) {
      // 复用等待中的会话时，更新用户本次进入客服携带的商品/订单上下文
      applyChatContext(exist, productId, productName, orderNo);
      // 只更新本次可能变化的字段：整行回写会把并发 AI 回复自增的 message_count 覆盖掉
      LambdaUpdateWrapper<CsSessionEntity> update = new LambdaUpdateWrapper<CsSessionEntity>()
          .eq(CsSessionEntity::getId, exist.getId())
          .set(CsSessionEntity::getProductId, exist.getProductId())
          .set(CsSessionEntity::getProductName, exist.getProductName())
          .set(CsSessionEntity::getProductImage, exist.getProductImage())
          .set(CsSessionEntity::getOrderNo, exist.getOrderNo());
      // 用户重新进入客服视为新一轮咨询：把「AI 转人工」时关掉的自动回复重新打开。
      // 但若最后一条消息是人工坐席发的，说明人工已接管，此时不能再让 AI 抢答
      if (!agentRepliedLast(exist.getId())) {
        exist.setAiEnabled(1);
        update.set(CsSessionEntity::getAiEnabled, 1);
      }
      sessionMapper.update(null, update);
      return exist;
    }
    CsSessionEntity s = new CsSessionEntity();
    s.setUserId(userId);
    s.setSessionId("CS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
    s.setChannel("H5");
    s.setStatus("WAITING");
    s.setMessageCount(0);
    applyChatContext(s, productId, productName, orderNo);
    sessionMapper.insert(s);
    return s;
  }

  /**
   * 会话最后一条消息是否由人工坐席发出（坐席回复即视为人工已接管）
   */
  private boolean agentRepliedLast(Long sessionId) {
    CsMessageEntity last = messageMapper.selectOne(
        new LambdaQueryWrapper<CsMessageEntity>()
            .eq(CsMessageEntity::getSessionId, sessionId)
            .orderByDesc(CsMessageEntity::getId)
            .last("LIMIT 1"));
    return last != null && "AGENT".equals(last.getSenderType());
  }

  /**
   * 写入会话的咨询上下文：
   * - 商品名称/图片由 APP 传入或从商品表实时补全，作为冗余字段避免商品删除后丢失
   * - 订单号只保存订单号，后台展示时再按订单号查询最新状态
   */
  private void applyChatContext(CsSessionEntity session, Long productId, String productName, String orderNo) {
    if (productId != null || (productName != null && !productName.isBlank())) {
      Map<String, Object> snapshot = csChatContextService.productSnapshot(productId);
      session.setProductId(productId);
      session.setProductName(productName);
      if (snapshot != null && snapshot.get("image") != null) {
        session.setProductImage(String.valueOf(snapshot.get("image")));
      }
    }
    if (orderNo != null && !orderNo.isBlank()) {
      session.setOrderNo(orderNo.trim());
    }
  }

  @Transactional
  public CsMessageEntity sendUserMessage(Long userId, Long sessionId, String content) {
    CsSessionEntity session = sessionMapper.selectById(sessionId);
    if (session == null) throw new IllegalArgumentException("会话不存在");
    if (!session.getUserId().equals(userId)) throw new IllegalStateException("无权访问该会话");

    CsMessageEntity msg = new CsMessageEntity();
    msg.setSessionId(sessionId);
    msg.setSenderType("USER");
    msg.setSenderId(userId);
    UserEntity u = userMapper.selectById(userId);
    msg.setSenderName(u != null ? u.getNickname() : "用户");
    msg.setContent(content);
    msg.setContentType("TEXT");
    msg.setReadFlag(0);
    messageMapper.insert(msg);

    // 状态流转单独定向更新，消息数走 SQL 自增，避免与异步 AI 回复的并发写互相覆盖
    if ("WAITING".equals(session.getStatus())) {
      session.setStatus("PROCESSING");
      sessionMapper.update(null, new LambdaUpdateWrapper<CsSessionEntity>()
          .eq(CsSessionEntity::getId, sessionId)
          .set(CsSessionEntity::getStatus, "PROCESSING"));
    }
    touchSession(sessionId);
    // AI 客服开启且凭据齐全时异步请求第三方智能体；否则保持原有内置关键词回复
    if (aiCsConfigService.current().usable()) {
      triggerAiReplyAfterCommit(userId, sessionId, content);
    } else {
      autoReply(session, content);
    }
    return msg;
  }

  /**
   * 事务提交后再触发 AI 回复
   * <p>
   * 异步线程需要读到已提交的用户消息与会话状态，否则会基于旧数据计数、覆盖消息数。
   */
  private void triggerAiReplyAfterCommit(Long userId, Long sessionId, String content) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      aiCsReplyService.replyAsync(sessionId, userId, content);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        aiCsReplyService.replyAsync(sessionId, userId, content);
      }
    });
  }

  private void autoReply(CsSessionEntity session, String userText) {
    String reply = CsKeywordReply.reply(userText);
    CsMessageEntity ai = new CsMessageEntity();
    ai.setSessionId(session.getId());
    ai.setSenderType("SYSTEM");
    ai.setSenderName("MOYUYO 助手");
    ai.setContent(reply);
    ai.setContentType("TEXT");
    ai.setReadFlag(0);
    messageMapper.insert(ai);
    touchSession(session.getId());
  }

  /**
   * 刷新会话最后消息时间并自增消息数
   * <p>
   * 用 SQL 自增而不是「读到内存加一再整行回写」，否则和异步 AI 回复的并发自增会互相覆盖计数。
   */
  private void touchSession(Long sessionId) {
    sessionMapper.update(null, new LambdaUpdateWrapper<CsSessionEntity>()
        .eq(CsSessionEntity::getId, sessionId)
        .set(CsSessionEntity::getLastMessageAt, LocalDateTime.now())
        .setSql("message_count = IFNULL(message_count, 0) + 1"));
  }

  @Transactional
  public void closeSession(Long userId, Long sessionId) {
    CsSessionEntity s = sessionMapper.selectById(sessionId);
    if (s == null || !s.getUserId().equals(userId)) throw new IllegalStateException("无权关闭该会话");
    // 只更新状态与关闭时间，避免整行回写覆盖并发 AI 回复自增的 message_count
    sessionMapper.update(null, new LambdaUpdateWrapper<CsSessionEntity>()
        .eq(CsSessionEntity::getId, sessionId)
        .set(CsSessionEntity::getStatus, "CLOSED")
        .set(CsSessionEntity::getCloseTime, LocalDateTime.now()));
  }

  public long countUnread(Long userId) {
    return messageMapper.selectCount(
        new LambdaQueryWrapper<CsMessageEntity>()
            .ne(CsMessageEntity::getSenderType, "USER")
            .eq(CsMessageEntity::getReadFlag, 0)
            .inSql(CsMessageEntity::getSessionId, "SELECT id FROM mo_cs_session WHERE user_id = " + userId));
  }

  /**
   * 客服概览：供 APP 消息页展示最近一次客服会话的摘要与未读数。
   * <p>
   * 这里不能复用 listMessages：那个方法会把客服消息标记为已读，
   * 若在消息页调用会导致未读角标被清零。
   */
  public Map<String, Object> latestOverview(Long userId) {
    Map<String, Object> overview = new HashMap<>();
    overview.put("unreadCount", countUnread(userId));
    CsSessionEntity session = sessionMapper.selectOne(
        new LambdaQueryWrapper<CsSessionEntity>()
            .eq(CsSessionEntity::getUserId, userId)
            .orderByDesc(CsSessionEntity::getLastMessageAt)
            .orderByDesc(CsSessionEntity::getId)
            .last("LIMIT 1"));
    if (session == null) {
      return overview;
    }
    overview.put("sessionId", session.getId());
    overview.put("status", session.getStatus());
    overview.put("productName", session.getProductName());
    overview.put("orderNo", session.getOrderNo());
    overview.put("lastMessageAt", session.getLastMessageAt());
    // 会话最后一条消息作为消息页预览（雪花 id 递增，直接取最大 id）
    CsMessageEntity last = messageMapper.selectOne(
        new LambdaQueryWrapper<CsMessageEntity>()
            .eq(CsMessageEntity::getSessionId, session.getId())
            .orderByDesc(CsMessageEntity::getId)
            .last("LIMIT 1"));
    if (last != null) {
      Map<String, Object> message = new HashMap<>();
      message.put("content", last.getContent());
      message.put("senderType", last.getSenderType());
      message.put("senderName", last.getSenderName());
      message.put("contentType", last.getContentType());
      message.put("createTime", last.getCreateTime());
      overview.put("lastMessage", message);
      overview.put("lastMessageAt", last.getCreateTime());
    }
    return overview;
  }
}
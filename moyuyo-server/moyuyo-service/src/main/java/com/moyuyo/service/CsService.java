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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 客服会话 + 消息服务。 */
@Service
@RequiredArgsConstructor
public class CsService {

  private final CsSessionMapper sessionMapper;
  private final CsMessageMapper messageMapper;
  private final UserMapper userMapper;

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
  public CsSessionEntity createSession(Long userId, String category) {
    CsSessionEntity exist = sessionMapper.selectOne(
        new LambdaQueryWrapper<CsSessionEntity>()
            .eq(CsSessionEntity::getUserId, userId)
            .eq(CsSessionEntity::getStatus, "WAITING")
            .orderByDesc(CsSessionEntity::getCreateTime)
            .last("LIMIT 1"));
    if (exist != null) return exist;
    CsSessionEntity s = new CsSessionEntity();
    s.setUserId(userId);
    s.setSessionId("CS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
    s.setChannel("H5");
    s.setStatus("WAITING");
    s.setMessageCount(0);
    sessionMapper.insert(s);
    return s;
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

    session.setLastMessageAt(LocalDateTime.now());
    session.setMessageCount(session.getMessageCount() == null ? 1 : session.getMessageCount() + 1);
    if ("WAITING".equals(session.getStatus())) session.setStatus("PROCESSING");
    sessionMapper.updateById(session);
    autoReply(session, content);
    return msg;
  }

  private void autoReply(CsSessionEntity session, String userText) {
    String reply;
    String text = userText == null ? "" : userText.toLowerCase();
    if (text.contains("物流") || text.contains("快递") || text.contains("发货")) {
      reply = "您的订单一般在支付后 24 小时内出库，可在「我的-订单」中查看物流。";
    } else if (text.contains("退款") || text.contains("退货")) {
      reply = "退款申请请到「我的-订单-申请退款」，原路退回 1-7 个工作日。";
    } else if (text.contains("发票")) {
      reply = "电子发票将在订单完成后 1-3 个工作日开具，请到「我的-发票」查看。";
    } else if (text.contains("优惠券") || text.contains("积分")) {
      reply = "积分可在「积分商城」兑换礼品，结算时也可抵扣（100 积分 = ¥1）。";
    } else if (text.contains("你好") || text.contains("hi")) {
      reply = "您好，我是 MOYUYO 智能客服小助手，请问需要什么帮助？";
    } else {
      reply = "已收到您的消息，客服将在工作时间尽快回复您。";
    }
    CsMessageEntity ai = new CsMessageEntity();
    ai.setSessionId(session.getId());
    ai.setSenderType("SYSTEM");
    ai.setSenderName("MOYUYO 助手");
    ai.setContent(reply);
    ai.setContentType("TEXT");
    ai.setReadFlag(0);
    messageMapper.insert(ai);
    session.setLastMessageAt(LocalDateTime.now());
    session.setMessageCount(session.getMessageCount() == null ? 2 : session.getMessageCount() + 1);
    sessionMapper.updateById(session);
  }

  @Transactional
  public void closeSession(Long userId, Long sessionId) {
    CsSessionEntity s = sessionMapper.selectById(sessionId);
    if (s == null || !s.getUserId().equals(userId)) throw new IllegalStateException("无权关闭该会话");
    s.setStatus("CLOSED");
    s.setCloseTime(LocalDateTime.now());
    sessionMapper.updateById(s);
  }

  public long countUnread(Long userId) {
    return messageMapper.selectCount(
        new LambdaQueryWrapper<CsMessageEntity>()
            .ne(CsMessageEntity::getSenderType, "USER")
            .eq(CsMessageEntity::getReadFlag, 0)
            .inSql(CsMessageEntity::getSessionId, "SELECT id FROM mo_cs_session WHERE user_id = " + userId));
  }
}
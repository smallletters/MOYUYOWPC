package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.CsMessageEntity;
import com.moyuyo.dao.admin.entity.CsSessionEntity;

import java.util.List;

/**
 * 客服在线聊天服务（消息持久化 + 会话状态联动）
 */
public interface CsMessageService {

  /**
   * 拉取会话所有消息，按时间升序
   */
  List<CsMessageEntity> listMessages(Long sessionId);

  /**
   * 增量轮询：上次时间戳之后的新消息
   *
   * @param sessionId 会话id
   * @param since 上次拉到的最新时间（可为 null = 取全部）
   * @return 新增消息列表
   */
  List<CsMessageEntity> pollMessages(Long sessionId, java.time.LocalDateTime since);

  /**
   * 发送一条消息（客服或用户均可，前端调用者传 senderType 区分）
   *
   * 同时会：
   * - 更新 mo_cs_message 表
   * - 增加 mo_cs_session.message_count
   * - 刷新 mo_cs_session.last_message_at
   * - 如果是客服发到首次会话，自动写 operator_id + 首次响应时长
   */
  CsMessageEntity sendMessage(CsMessageEntity message);

  /**
   * 把某会话所有 USER 类型消息标记为已读
   */
  int markRead(Long sessionId);

  /**
   * 关闭会话（写入 close_time + status=CLOSED）
   */
  CsSessionEntity closeSession(Long sessionId);

  /**
   * 转接客服：修改 operator_id + 写一条 SYSTEM 提示消息
   */
  CsSessionEntity transferSession(Long sessionId, Long newOperatorId, String operatorName);
}

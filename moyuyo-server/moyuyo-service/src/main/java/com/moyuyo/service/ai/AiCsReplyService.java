package com.moyuyo.service.ai;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.admin.entity.CsAiUsageEntity;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import com.moyuyo.dao.admin.entity.CsSessionEntity;
import com.moyuyo.dao.admin.mapper.CsAiUsageMapper;
import com.moyuyo.dao.admin.mapper.CsMessageMapper;
import com.moyuyo.dao.admin.mapper.CsSessionMapper;
import com.moyuyo.service.CsKeywordReply;
import com.moyuyo.service.config.AiCsConfig;
import com.moyuyo.service.config.AiCsConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 客服回复编排
 * <p>
 * 职责：把「用户提问 → 第三方智能体 → 回复写回消息表」这段链路异步化，
 * 保证 {@code CsController} 的发送消息接口不被第三方耗时拖慢（APP 端靠轮询拿回复）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>异步执行，失败只降级不抛错，避免影响用户发消息</li>
 *   <li>转人工后（ai_enabled=0）不再自动回复，防止机器人与人工坐席抢答</li>
 *   <li>会话已关闭时直接丢弃，避免脏写</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCsReplyService {

  /** 消息发送方：SYSTEM 表示系统/AI 自动回复，前端渲染为机器人气泡 */
  private static final String SENDER_SYSTEM = "SYSTEM";

  /** 机器人展示名（AI 生成内容必须可识别，不能伪装成真人客服） */
  private static final String AI_STAFF_NAME = "MOYUYO 助手";

  /** 智能体请求转人工时的提示语 */
  private static final String HUMAN_TRANSFER_TIP = "正在为您转接人工客服，请稍候。";

  /** 第三方不可用时的兜底话术（不自动转人工，避免一次故障永久关掉该会话的 AI） */
  private static final String FALLBACK_TIP = "抱歉，我这边暂时没能回答这个问题，您可以换个说法，或稍后再试。";

  /** 触发限流时的提示语 */
  private static final String RATE_LIMIT_TIP = "您发送得太频繁了，请稍后再试；如需紧急处理可转人工客服。";

  private final List<AiCsProvider> providers;
  private final CsAiProductContextService productContextService;
  private final AiCsConfigService aiCsConfigService;
  private final AiCsRateLimiter aiCsRateLimiter;
  private final CsSessionMapper sessionMapper;
  private final CsMessageMapper messageMapper;
  private final CsAiUsageMapper usageMapper;

  /**
   * 异步生成 AI 回复
   *
   * @param sessionId 会话 ID
   * @param userId    用户 ID（用于第三方侧的用户标识）
   * @param userText  用户本轮问题
   */
  @Async("moyuyoTaskExecutor")
  public void replyAsync(Long sessionId, Long userId, String userText) {
    try {
      handle(sessionId, userId, userText);
    } catch (Exception e) {
      // 异步线程异常不会回传给调用方，这里必须自己兜住并记录
      log.warn("AI 客服回复失败，sessionId={}", sessionId, e);
    }
  }

  private void handle(Long sessionId, Long userId, String userText) {
    CsSessionEntity session = sessionMapper.selectById(sessionId);
    if (session == null) return;
    // 会话已关闭 / 已转人工：不再自动回复
    if ("CLOSED".equals(session.getStatus())) return;
    if (session.getAiEnabled() != null && session.getAiEnabled() == 0) return;

    // 配置在调用时读取，后台修改后最多 30 秒生效，无需重启
    AiCsConfig config = aiCsConfigService.current();
    if (!config.usable()) {
      // 消息已入队、此时 AI 却被后台关闭：回退到内置关键词回复，避免用户这一轮无人应答
      appendSystemMessage(session, CsKeywordReply.reply(userText));
      return;
    }
    AiCsProvider provider = resolveProvider(config.provider());
    if (provider == null) {
      log.warn("未找到 AI 客服实现：provider={}，请检查后台配置", config.provider());
      // 配置了尚未实现的服务商时同样回退关键词回复，避免用户这一轮无人应答
      appendSystemMessage(session, CsKeywordReply.reply(userText));
      return;
    }

    // 单用户限流：超限只提示，不调用第三方，避免 token 被刷
    if (!aiCsRateLimiter.tryAcquire(userId)) {
      log.info("AI 客服触发限流：sessionId={}, userId={}", sessionId, userId);
      appendSystemMessage(session, RATE_LIMIT_TIP);
      return;
    }

    // 扣子侧没有商城数据库，先把检索到的在售商品资料拼进提问，避免它凭空回答「没有该商品」
    String productContext = productContextService.build(userText);
    String prompt = productContext.isEmpty() ? userText : productContext + "【用户问题】" + userText;
    AiReplyResult result = provider.reply(toAiUserId(userId), session.getAiConversationId(), prompt);
    // 无论成功失败都记一条用量，便于成本核算与故障率统计
    recordUsage(sessionId, userId, provider.getProviderName(), result);

    if (result == null || !result.hasText()) {
      // 兜底话术同样先确认会话仍由 AI 接待：第三方耗时期间人工可能已接管
      if (stillAiReachable(sessionId)) {
        appendSystemMessage(session, FALLBACK_TIP);
      }
      return;
    }

    // 复用第三方会话 ID，下一轮由对方维护上下文
    if (result.conversationId() != null && !result.conversationId().isBlank()
        && !result.conversationId().equals(session.getAiConversationId())) {
      // 只更新该字段：整行 updateById 会把并发的人工接管状态又覆盖回「AI 开启」
      sessionMapper.update(null, new LambdaUpdateWrapper<CsSessionEntity>()
          .eq(CsSessionEntity::getId, session.getId())
          .set(CsSessionEntity::getAiConversationId, result.conversationId()));
      session.setAiConversationId(result.conversationId());
    }

    String marker = config.humanMarker();
    boolean needHuman = result.needHuman()
        || (marker != null && !marker.isBlank() && result.text().contains(marker));

    // 转人工标记不能展示给用户
    String reply = marker == null || marker.isBlank()
        ? result.text().trim()
        : result.text().replace(marker, "").trim();
    if (reply.isEmpty()) {
      reply = HUMAN_TRANSFER_TIP;
      needHuman = true;
    }
    // 写库前再确认一次：第三方调用耗时期间人工坐席可能已接管、用户可能已关闭会话
    if (!stillAiReachable(sessionId)) {
      log.info("AI 回复已放弃：会话状态在调用期间变更，sessionId={}", sessionId);
      return;
    }
    appendSystemMessage(session, reply);

    if (needHuman) {
      // 正文为空时 reply 已被替换成转人工提示，此处不要再追加一次，否则用户会收到两条相同提示
      if (!HUMAN_TRANSFER_TIP.equals(reply)) {
        appendSystemMessage(session, HUMAN_TRANSFER_TIP);
      }
      transferToHuman(session);
    }
  }

  /**
   * 按名称选择实现，支持后续接入多家供应商而不改业务代码
   */
  private AiCsProvider resolveProvider(String providerName) {
    if (providerName == null || providerName.isBlank()) return null;
    return providers.stream()
        .filter(p -> providerName.equalsIgnoreCase(p.getProviderName()))
        .findFirst()
        .orElse(null);
  }

  /**
   * 记录本次调用用量
   * <p>
   * 埋点失败不能影响用户会话：这里吞掉异常只打日志。
   */
  private void recordUsage(Long sessionId, Long userId, String providerName, AiReplyResult result) {
    try {
      AiUsage usage = result == null ? null : result.usage();
      boolean success = result != null && result.hasText();

      CsAiUsageEntity record = new CsAiUsageEntity();
      record.setSessionId(sessionId);
      record.setUserId(userId);
      record.setProvider(providerName);
      record.setRequestId(usage == null ? null : usage.requestId());
      record.setConversationId(result == null ? null : result.conversationId());
      record.setInputTokens(usage == null ? 0 : usage.inputTokens());
      record.setOutputTokens(usage == null ? 0 : usage.outputTokens());
      record.setTotalTokens(usage == null ? 0 : usage.totalTokens());
      record.setDurationMs(usage == null ? 0 : (int) usage.durationMs());
      record.setSuccess(success ? 1 : 0);
      record.setErrorMsg(success ? null : truncate(result == null ? null : result.errorMessage()));
      usageMapper.insert(record);
    } catch (Exception e) {
      log.warn("记录 AI 客服用量失败，sessionId={}", sessionId, e);
    }
  }

  /** 失败原因截断到列宽，避免超长异常信息导致插入失败 */
  private String truncate(String text) {
    if (text == null) return null;
    return text.length() <= 500 ? text : text.substring(0, 500);
  }

  /**
   * 写一条机器人消息，并同步会话的消息数 / 最后消息时间
   */
  private void appendSystemMessage(CsSessionEntity session, String text) {
    CsMessageEntity msg = new CsMessageEntity();
    msg.setSessionId(session.getId());
    msg.setSenderType(SENDER_SYSTEM);
    msg.setSenderName(AI_STAFF_NAME);
    msg.setContentType("TEXT");
    msg.setContent(text);
    // 未读：让 APP 轮询时能弹出提醒
    msg.setReadFlag(0);
    msg.setCreateTime(LocalDateTime.now());
    messageMapper.insert(msg);

    // 消息数交给数据库自增：并发场景下读-改-写会互相覆盖
    sessionMapper.update(null, new LambdaUpdateWrapper<CsSessionEntity>()
        .eq(CsSessionEntity::getId, session.getId())
        .set(CsSessionEntity::getLastMessageAt, msg.getCreateTime())
        .setSql("message_count = IFNULL(message_count, 0) + 1"));
  }

  /**
   * 关闭该会话的 AI 自动回复，并退回待接入队列，后台坐席可见
   */
  private void transferToHuman(CsSessionEntity session) {
    // 只改这两个字段，避免整行回写把并发修改（如坐席接管）覆盖掉
    sessionMapper.update(null, new LambdaUpdateWrapper<CsSessionEntity>()
        .eq(CsSessionEntity::getId, session.getId())
        .set(CsSessionEntity::getAiEnabled, 0)
        .set(CsSessionEntity::getStatus, "WAITING"));
    log.info("会话已转人工：sessionId={}", session.getId());
  }

  /**
   * 第三方调用结束后复查会话：已关闭或已转人工则不再写入 AI 回复
   */
  private boolean stillAiReachable(Long sessionId) {
    CsSessionEntity latest = sessionMapper.selectById(sessionId);
    if (latest == null) return false;
    if ("CLOSED".equals(latest.getStatus())) return false;
    return latest.getAiEnabled() == null || latest.getAiEnabled() != 0;
  }

  /**
   * 传给第三方的用户标识：脱敏为纯 ID，不携带手机号、邮箱等个人信息
   */
  private String toAiUserId(Long userId) {
    return "u" + userId;
  }
}

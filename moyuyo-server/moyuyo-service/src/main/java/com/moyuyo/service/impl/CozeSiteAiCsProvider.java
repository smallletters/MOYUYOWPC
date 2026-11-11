package com.moyuyo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.service.ai.AiCsProvider;
import com.moyuyo.service.ai.AiReplyResult;
import com.moyuyo.service.ai.AiUsage;
import com.moyuyo.service.config.AiCsConfig;
import com.moyuyo.service.config.AiCsConfigService;
import com.moyuyo.service.config.AiCsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 扣子编程（coze.site）智能客服实现
 * <p>
 * 与 {@link CozeAiCsProvider} 的区别：扣子编程的应用没有 bot_id / API 发布渠道，
 * 而是在部署页提供一个自定义域名 + /stream_run 的流式接口：
 * <pre>
 * POST https://{部署域名}/stream_run
 * Authorization: Bearer {API Token}
 * Accept: text/event-stream
 * {
 *   "content": {"query": {"prompt": [{"type": "text", "content": {"text": "用户问题"}}]}},
 *   "type": "query",
 *   "session_id": "...",
 *   "project_id": 7632333768757608448
 * }
 * </pre>
 * 返回 SSE，事件类型见 data.type：
 * <ul>
 *   <li>answer：增量文本，需要按顺序拼接成完整回复</li>
 *   <li>message_end：结束事件，带 code（0 成功）、token_cost、time_cost_ms</li>
 *   <li>error：失败事件</li>
 *   <li>message_start / tool_request / tool_response：与回复正文无关，忽略</li>
 * </ul>
 * session_id 复用会话表的 ai_conversation_id：首次自动生成，后续追问沿用，由对方维护上下文。
 */
@Slf4j
@Component
public class CozeSiteAiCsProvider implements AiCsProvider {

  /** 实现名，与后台「服务商」下拉的 value 保持一致 */
  private static final String PROVIDER_NAME = "coze-site";

  /** 部署页固定路径 */
  private static final String STREAM_PATH = "/stream_run";

  private static final String EVENT_ANSWER = "answer";
  private static final String EVENT_MESSAGE_START = "message_start";
  private static final String EVENT_MESSAGE_END = "message_end";
  private static final String EVENT_ERROR = "error";

  /** 扣子编程返回的业务成功码（字符串形式） */
  private static final String CODE_SUCCESS = "0";

  private final AiCsConfigService configService;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  public CozeSiteAiCsProvider(AiCsProperties properties, AiCsConfigService configService,
                              ObjectMapper objectMapper) {
    this.configService = configService;
    this.objectMapper = objectMapper;
    // 流式接口：读取超时必须覆盖整段回复生成时间，与 OpenAPI 分开配置
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
    this.restTemplate = new RestTemplate(factory);
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public AiReplyResult reply(String userId, String conversationId, String text) {
    if (text == null || text.isBlank()) return AiReplyResult.empty();

    AiCsConfig config = configService.current();
    if (!config.usable()) return AiReplyResult.empty();

    long start = System.currentTimeMillis();
    String baseUrl = trimSlash(config.baseUrl());
    String projectId = config.projectId() == null ? "" : config.projectId().trim();
    if (baseUrl.isBlank() || projectId.isBlank()) {
      // 后台配置不完整时给出明确原因，便于管理端排查
      return AiReplyResult.failure("扣子编程配置不完整：需填写接口地址与 Project ID",
          System.currentTimeMillis() - start);
    }

    // 复用会话级 session_id，由扣子侧维护多轮上下文
    String sessionId = conversationId == null || conversationId.isBlank() ? newSessionId() : conversationId;
    Map<String, Object> payload = buildPayload(sessionId, projectId, text);
    // 兼容管理员直接粘贴部署页的完整地址（已含 /stream_run 时不再重复拼接）
    String url = baseUrl.endsWith(STREAM_PATH) ? baseUrl : baseUrl + STREAM_PATH;

    try {
      StreamResult stream = restTemplate.execute(url, HttpMethod.POST, request -> {
        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        request.getHeaders().setBearerAuth(config.token());
        request.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        objectMapper.writeValue(request.getBody(), payload);
      }, response -> readStream(response.getBody()));

      if (stream == null) {
        return AiReplyResult.failure("扣子编程未返回响应", System.currentTimeMillis() - start);
      }

      AiUsage usage = new AiUsage(stream.requestId, stream.inputTokens, stream.outputTokens, stream.totalTokens,
          stream.timeCostMs > 0 ? stream.timeCostMs : System.currentTimeMillis() - start);

      if (!CODE_SUCCESS.equals(stream.code)) {
        return new AiReplyResult("", sessionId, false, usage,
            "扣子编程返回失败：" + defaultText(stream.message, "未知错误"));
      }

      String answer = stream.answer.toString().trim();
      if (answer.isEmpty()) {
        return new AiReplyResult("", sessionId, false, usage, "扣子编程未返回内容");
      }
      return new AiReplyResult(answer, sessionId, false, usage, null);
    } catch (Exception e) {
      // 网络异常、鉴权失败、超时等统一降级，由上层写兜底话术
      log.warn("调用扣子编程失败：sessionId={}", sessionId, e);
      return AiReplyResult.failure(e.getMessage(), System.currentTimeMillis() - start);
    }
  }

  /**
   * 组装请求体，字段与部署页「调用示例」保持一致
   */
  private Map<String, Object> buildPayload(String sessionId, String projectId, String text) {
    Map<String, Object> prompt = new LinkedHashMap<>();
    prompt.put("type", "text");
    prompt.put("content", Map.of("text", text));

    Map<String, Object> query = new LinkedHashMap<>();
    query.put("prompt", List.of(prompt));

    Map<String, Object> content = new LinkedHashMap<>();
    content.put("query", query);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("content", content);
    payload.put("type", "query");
    payload.put("session_id", sessionId);
    payload.put("project_id", projectIdValue(projectId));
    return payload;
  }

  /**
   * project_id 在示例中有数字与字符串两种写法：纯数字按长整型发送，其余原样发送
   */
  private Object projectIdValue(String projectId) {
    if (projectId.chars().allMatch(Character::isDigit)) {
      try {
        return Long.parseLong(projectId);
      } catch (NumberFormatException ignored) {
        // 超出 long 范围时按字符串发送
      }
    }
    return projectId;
  }

  /**
   * 逐行读取 SSE，以空行分隔事件块，只处理 data: 行
   */
  private StreamResult readStream(InputStream body) throws IOException {
    StreamResult result = new StreamResult();
    if (body == null) return result;

    StringBuilder block = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          handleBlock(result, block);
          continue;
        }
        // 同一事件块内的多条 data 行按拼接处理（扣子编程实际只发送一条）
        if (line.startsWith("data:")) {
          block.append(line.substring(5).trim());
        }
      }
      handleBlock(result, block);
    }
    return result;
  }

  /**
   * 解析单个事件块，按 data.type 分发
   * <p>
   * 实际报文把业务字段嵌在 content 下，外层只有 type / reply_id 等公共字段：
   * <pre>
   * {"type":"answer","content":{"answer":"您好",...}}
   * {"type":"message_end","content":{"message_end":{"code":"0","token_cost":{...},"time_cost_ms":2367}}}
   * </pre>
   * 因此正文与结束信息都从 content 读取。
   */
  private void handleBlock(StreamResult result, StringBuilder block) {
    if (block.length() == 0) return;
    String data = block.toString();
    block.setLength(0);
    try {
      JsonNode node = objectMapper.readTree(data);
      String type = node.path("type").asText("");
      JsonNode content = node.path("content");
      switch (type) {
        case EVENT_ANSWER -> result.answer.append(content.path("answer").asText(""));
        case EVENT_MESSAGE_START -> {
          // reply_id 在一次问答内保持不变，用作用量表的请求标识
          String replyId = node.path("reply_id").asText("");
          result.requestId = replyId.isBlank() ? node.path("msg_id").asText(null) : replyId;
        }
        case EVENT_MESSAGE_END -> {
          JsonNode end = content.path("message_end");
          if (end.isObject()) {
            result.code = end.path("code").asText(CODE_SUCCESS);
            result.message = end.path("message").asText("");
            JsonNode cost = end.path("token_cost");
            if (cost.isObject()) {
              result.inputTokens = cost.path("input_tokens").asInt(0);
              result.outputTokens = cost.path("output_tokens").asInt(0);
              result.totalTokens = cost.path("total_tokens").asInt(0);
            }
            result.timeCostMs = end.path("time_cost_ms").asLong(0L);
          }
        }
        case EVENT_ERROR -> {
          JsonNode error = content.path("error");
          result.code = error.path("code").asText("1");
          result.message = error.path("message").asText("扣子编程返回错误");
        }
        default -> {
          // message_start / tool_request / tool_response 等事件不参与正文拼接
        }
      }
    } catch (Exception e) {
      // 非 JSON 的 data（心跳等）直接忽略，不能影响整段回复
      log.debug("忽略无法解析的扣子编程流事件：{}", abbreviate(data));
    }
  }

  /**
   * 生成 22 位 base64url 会话 ID，格式与扣子编程示例一致
   */
  private String newSessionId() {
    UUID uuid = UUID.randomUUID();
    ByteBuffer buffer = ByteBuffer.allocate(16);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }

  private String trimSlash(String url) {
    if (url == null) return "";
    String trimmed = url.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  private String defaultText(String text, String fallback) {
    return text == null || text.isBlank() ? fallback : text;
  }

  private String abbreviate(String text) {
    return text.length() <= 200 ? text : text.substring(0, 200);
  }

  /**
   * SSE 解析过程中的可变累积结果
   */
  private static class StreamResult {
    private final StringBuilder answer = new StringBuilder();
    private String requestId;
    private String code = CODE_SUCCESS;
    private String message = "";
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private long timeCostMs;
  }
}

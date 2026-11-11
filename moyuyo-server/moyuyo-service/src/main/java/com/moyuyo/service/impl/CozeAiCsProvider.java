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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扣子（Coze）智能客服实现
 * <p>
 * 常驻注册（不再按 yml 条件装配）：后台开启 AI 客服后无需重启即可生效，
 * bot_id / token / 地址等每次调用从 {@link AiCsConfigService} 实时读取。
 * <p>
 * 调用流程（扣子 v3 Chat API，非流式）：
 * <ol>
 *   <li>POST /v3/chat 发起对话，拿到 chat_id + conversation_id（状态 in_progress）</li>
 *   <li>GET /v3/chat/retrieve 轮询状态，直到 completed / failed</li>
 *   <li>GET /v3/chat/message/list 取 role=assistant 且 type=answer 的消息作为回复</li>
 * </ol>
 * 任何失败都返回 {@link AiReplyResult#failure}，由上层降级成兜底话术，不阻塞用户会话。
 *
 * @see <a href="https://docs.coze.cn/developer_guides_chat_v3">扣子 v3/chat 文档</a>
 */
@Slf4j
@Component
public class CozeAiCsProvider implements AiCsProvider {

  /** 扣子返回的业务成功码 */
  private static final int COZE_SUCCESS = 0;

  private final AiCsProperties properties;
  private final AiCsConfigService configService;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  public CozeAiCsProvider(AiCsProperties properties, AiCsConfigService configService,
                          ObjectMapper objectMapper) {
    this.properties = properties;
    this.configService = configService;
    this.objectMapper = objectMapper;
    // 独立的 RestTemplate：AI 接口超时策略与支付/物流不同，不能共用全局实例
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
    this.restTemplate = new RestTemplate(factory);
  }

  @Override
  public AiReplyResult reply(String userId, String conversationId, String text) {
    if (text == null || text.isBlank()) return AiReplyResult.empty();

    AiCsConfig config = configService.current();
    if (!config.usable()) {
      // 开关关闭或凭据不全：交给上层走内置关键词回复
      return AiReplyResult.empty();
    }

    long startedAt = System.currentTimeMillis();
    try {
      ChatHandle handle = startChat(config, userId, conversationId, text);
      JsonNode finalData = waitForCompleted(config, handle);
      String answer = fetchAnswer(config, handle);
      long durationMs = System.currentTimeMillis() - startedAt;
      AiUsage usage = toUsage(handle, finalData, durationMs);
      if (answer == null || answer.isBlank()) {
        // 对话完成但没有 answer（如命中安全策略）：仍记用量，便于排查
        return new AiReplyResult(null, handle.conversationId(), false, usage, "扣子未返回有效回答");
      }
      return new AiReplyResult(answer, handle.conversationId(), false, usage, null);
    } catch (Exception e) {
      // 外部依赖异常不向上抛：客服会话不能因为第三方故障而不可用
      log.warn("扣子对话失败，provider={}, userId={}", getProviderName(), userId, e);
      return AiReplyResult.failure(e.getMessage(), System.currentTimeMillis() - startedAt);
    }
  }

  /**
   * 发起对话，返回 chat_id 与 conversation_id
   */
  private ChatHandle startChat(AiCsConfig config, String userId, String conversationId, String text) throws Exception {
    StringBuilder url = new StringBuilder(baseUrl(config)).append("/v3/chat");
    if (conversationId != null && !conversationId.isBlank()) {
      // conversation_id 复用后，历史消息由扣子侧维护，无需每次重传
      url.append("?conversation_id=").append(conversationId);
    }

    Map<String, Object> userMessage = new LinkedHashMap<>();
    userMessage.put("role", "user");
    userMessage.put("content", text);
    userMessage.put("content_type", "text");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("bot_id", config.botId());
    body.put("user_id", userId);
    body.put("stream", false);
    body.put("auto_save_history", true);
    body.put("additional_messages", List.of(userMessage));

    JsonNode data = exchange(config, url.toString(), HttpMethod.POST, body);
    String chatId = data.path("id").asText();
    String convId = data.path("conversation_id").asText();
    if (chatId.isBlank()) {
      throw new IllegalStateException("扣子未返回 chat_id");
    }
    if (convId.isBlank()) {
      // 后续 retrieve / message_list 都依赖该参数，缺失时提前报明确错误，而不是拼出空参数
      throw new IllegalStateException("扣子未返回 conversation_id");
    }
    return new ChatHandle(chatId, convId);
  }

  /**
   * 轮询直到对话完成，返回 completed 状态的对话详情（含 usage）
   */
  private JsonNode waitForCompleted(AiCsConfig config, ChatHandle handle) throws Exception {
    long deadline = System.currentTimeMillis() + Math.max(1000, config.timeoutMs());
    String retrieveUrl = baseUrl(config) + "/v3/chat/retrieve?conversation_id=" + handle.conversationId()
        + "&chat_id=" + handle.chatId();

    while (System.currentTimeMillis() < deadline) {
      JsonNode data = exchange(config, retrieveUrl, HttpMethod.GET, null);
      String status = data.path("status").asText();
      if ("completed".equals(status)) {
        return data;
      }
      if ("failed".equals(status) || "canceled".equals(status) || "requires_action".equals(status)) {
        throw new IllegalStateException("扣子对话未正常完成，status=" + status);
      }
      // 下限保护：轮询间隔配成 0 会导致死循环式忙等，直接烧满一个线程
      Thread.sleep(Math.max(200L, properties.getPollIntervalMs()));
    }
    throw new IllegalStateException("扣子对话超时（" + config.timeoutMs() + "ms）");
  }

  /**
   * 从 completed 详情中解析 token 用量（字段缺失时按 0 处理，不影响对话本身）
   */
  private AiUsage toUsage(ChatHandle handle, JsonNode data, long durationMs) {
    JsonNode usage = data == null ? null : data.path("usage");
    int input = usage == null ? 0 : usage.path("input_count").asInt(0);
    int output = usage == null ? 0 : usage.path("output_count").asInt(0);
    int total = usage == null ? 0 : usage.path("token_count").asInt(input + output);
    return new AiUsage(handle.chatId(), input, output, total, durationMs);
  }

  /**
   * 取本轮 assistant 的 answer 消息（多条时按顺序拼接）
   */
  private String fetchAnswer(AiCsConfig config, ChatHandle handle) throws Exception {
    String url = baseUrl(config) + "/v3/chat/message/list?conversation_id=" + handle.conversationId()
        + "&chat_id=" + handle.chatId();
    JsonNode data = exchange(config, url, HttpMethod.GET, null);
    if (!data.isArray()) return null;

    StringBuilder sb = new StringBuilder();
    for (JsonNode msg : data) {
      if (!"assistant".equals(msg.path("role").asText())) continue;
      if (!"answer".equals(msg.path("type").asText())) continue;
      String content = msg.path("content").asText("");
      if (content.isBlank()) continue;
      if (!sb.isEmpty()) sb.append('\n');
      sb.append(content);
    }
    return sb.toString();
  }

  /**
   * 统一请求与错误处理：返回扣子响应体里的 data 节点
   */
  private JsonNode exchange(AiCsConfig config, String url, HttpMethod method, Object body) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(config.token());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));

    ResponseEntity<String> response = restTemplate.exchange(
        url, method, new HttpEntity<>(body, headers), String.class);

    String raw = response.getBody();
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("扣子返回空响应");
    }
    JsonNode root = objectMapper.readTree(raw);
    int code = root.path("code").asInt(-1);
    if (code != COZE_SUCCESS) {
      throw new IllegalStateException("扣子返回错误 code=" + code + ", msg=" + root.path("msg").asText());
    }
    return root.path("data");
  }

  /**
   * 去掉末尾斜杠，避免拼出双斜杠
   */
  private String baseUrl(AiCsConfig config) {
    String url = config.baseUrl() == null || config.baseUrl().isBlank()
        ? "https://api.coze.cn" : config.baseUrl().trim();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  @Override
  public String getProviderName() {
    return "coze";
  }

  /** 单次对话句柄 */
  private record ChatHandle(String chatId, String conversationId) {
  }
}

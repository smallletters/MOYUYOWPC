package com.moyuyo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.service.AppPushService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * App Push 实现：Google FCM HTTP v1。
 * <p>
 * 启用方式：
 * <pre>
 * MOYUYO_FCM_ENABLED=true
 * MOYUYO_FCM_SERVICE_ACCOUNT_FILE=/path/to/firebase-service-account.json
 * </pre>
 * 服务账号 JSON 从 Firebase 控制台「项目设置 → 服务账号 → 生成新的私钥」下载。
 * <p>
 * 说明：
 * - 不引入 firebase-admin SDK，使用 JDK HttpClient + RS256 JWT 换取 OAuth2 access token，
 *   减少依赖体积，且便于在离线构建环境编译；
 * - access token 缓存到过期前 60 秒，避免每条推送都走一次 OAuth；
 * - 初始化失败（文件缺失/私钥格式错误）只记录日志并标记不可用，不阻塞应用启动。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "moyuyo.push.fcm.enabled", havingValue = "true")
public class FcmAppPushServiceImpl implements AppPushService {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String OAUTH_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
  private static final String FCM_SEND_ENDPOINT = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
  private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

  /** JWT 有效期（秒） */
  private static final long JWT_TTL_SECONDS = 3600;

  /** access token 提前刷新秒数 */
  private static final long TOKEN_REFRESH_AHEAD_SECONDS = 60;

  @Value("${moyuyo.push.fcm.service-account-file:}")
  private String serviceAccountFile;

  private HttpClient httpClient;
  private String projectId;
  private String clientEmail;
  private PrivateKey privateKey;

  /** 配置是否可用（文件缺失/解析失败时为 false） */
  private volatile boolean available;

  private volatile String accessToken;
  private volatile long accessTokenExpireAt;

  @PostConstruct
  void init() {
    try {
      if (serviceAccountFile == null || serviceAccountFile.isBlank()) {
        throw new IllegalStateException("moyuyo.push.fcm.service-account-file 未配置");
      }
      JsonNode account = JSON.readTree(new File(serviceAccountFile.trim()));
      projectId = account.path("project_id").asText();
      clientEmail = account.path("client_email").asText();
      String pem = account.path("private_key").asText();
      if (projectId.isBlank() || clientEmail.isBlank() || pem.isBlank()) {
        throw new IllegalStateException("服务账号 JSON 缺少 project_id/client_email/private_key");
      }
      privateKey = parsePrivateKey(pem);
      httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .build();
      available = true;
      log.info("[push] FCM 初始化成功: projectId={}", projectId);
    } catch (Exception e) {
      available = false;
      log.error("[push] FCM 初始化失败，App Push 将不可用: {}", e.getMessage());
    }
  }

  @Override
  public boolean supports(String platform) {
    // iOS 走 APNs 直连，其余平台（Android / 未知）统一走 FCM
    return platform == null || !platform.trim().toUpperCase().startsWith("IOS");
  }

  @Override
  public void send(String pushToken, String title, String body) {
    if (!available) {
      throw new IllegalStateException("FCM 未配置或初始化失败");
    }
    if (pushToken == null || pushToken.isBlank()) {
      throw new IllegalArgumentException("pushToken 为空");
    }
    try {
      HttpResponse<String> response = postMessage(getAccessToken(), pushToken, title, body);
      if (response.statusCode() == 401) {
        // access token 失效：刷新后重试一次
        invalidateAccessToken();
        response = postMessage(getAccessToken(), pushToken, title, body);
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "FCM 发送失败: HTTP " + response.statusCode() + " " + abbreviate(response.body()));
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("FCM 请求异常: " + e.getMessage(), e);
    }
  }

  /**
   * 发送一条 FCM 消息
   */
  private HttpResponse<String> postMessage(String token, String pushToken, String title, String body)
      throws Exception {
    Map<String, Object> notification = new LinkedHashMap<>();
    notification.put("title", title);
    notification.put("body", body);

    Map<String, Object> message = new LinkedHashMap<>();
    message.put("token", pushToken);
    message.put("notification", notification);

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("message", message);

    String url = String.format(FCM_SEND_ENDPOINT, projectId);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(root), StandardCharsets.UTF_8))
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  /**
   * 获取 OAuth2 access token（带缓存）
   */
  private synchronized String getAccessToken() throws Exception {
    long now = Instant.now().getEpochSecond();
    if (accessToken != null && now < accessTokenExpireAt - TOKEN_REFRESH_AHEAD_SECONDS) {
      return accessToken;
    }
    String assertion = buildAssertion(now);
    String form = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + assertion;
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(OAUTH_TOKEN_ENDPOINT))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("FCM OAuth 失败: HTTP " + response.statusCode() + " " + abbreviate(response.body()));
    }
    JsonNode json = JSON.readTree(response.body());
    String token = json.path("access_token").asText();
    long expiresIn = json.path("expires_in").asLong(JWT_TTL_SECONDS);
    if (token.isBlank()) {
      throw new IllegalStateException("FCM OAuth 响应缺少 access_token");
    }
    accessToken = token;
    accessTokenExpireAt = now + expiresIn;
    return accessToken;
  }

  private synchronized void invalidateAccessToken() {
    accessToken = null;
    accessTokenExpireAt = 0;
  }

  /**
   * 构造 RS256 JWT assertion（服务账号 OAuth2 标准流程）
   */
  private String buildAssertion(long now) throws Exception {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "RS256");
    header.put("typ", "JWT");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("iss", clientEmail);
    payload.put("scope", SCOPE);
    payload.put("aud", OAUTH_TOKEN_ENDPOINT);
    payload.put("iat", now);
    payload.put("exp", now + JWT_TTL_SECONDS);

    String signingInput = base64Url(JSON.writeValueAsBytes(header)) + "." + base64Url(JSON.writeValueAsBytes(payload));
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(privateKey);
    signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
    return signingInput + "." + base64Url(signer.sign());
  }

  /**
   * 解析服务账号 JSON 中的 RSA 私钥（PKCS#8 PEM）
   */
  private PrivateKey parsePrivateKey(String pem) throws Exception {
    String normalized = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(normalized);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  private String base64Url(byte[] data) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
  }

  /**
   * 截断第三方响应，避免日志/异常消息过长
   */
  private String abbreviate(String text) {
    if (text == null) {
      return "";
    }
    return text.length() <= 300 ? text : text.substring(0, 300) + "...";
  }
}

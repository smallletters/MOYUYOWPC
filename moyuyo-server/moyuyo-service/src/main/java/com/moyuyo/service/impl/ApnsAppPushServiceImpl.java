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
 * App Push 实现：APNs HTTP/2（iOS 直连，不经过 FCM）。
 * <p>
 * 启用方式：
 * <pre>
 * MOYUYO_APNS_ENABLED=true
 * MOYUYO_APNS_KEY_FILE=/path/to/AuthKey_XXXXXXXXXX.p8
 * MOYUYO_APNS_KEY_ID=XXXXXXXXXX
 * MOYUYO_APNS_TEAM_ID=TEAMID
 * MOYUYO_APNS_BUNDLE_ID=com.moyuyo.app
 * MOYUYO_APNS_ENVIRONMENT=prod   # prod / dev(sandbox)
 * </pre>
 * <p>
 * 实现要点：
 * - 使用 .p8 私钥（ES256 JWT）鉴权，JWT 缓存 55 分钟（APNs 要求每小时刷新）；
 * - Java 的 SHA256withECDSA 输出 DER 编码，而 JWT ES256 要求 R||S 固定长度拼接，
 *   此处自行做 DER → raw 转换；
 * - 401/403 视为 JWT 失效，刷新后重试一次；
 * - 初始化失败只记录日志并标记不可用，不阻塞应用启动。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "moyuyo.push.apns.enabled", havingValue = "true")
public class ApnsAppPushServiceImpl implements AppPushService {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String PROD_HOST = "https://api.push.apple.com";
  private static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";

  /** APNs JWT 复用时长（秒）：官方要求 20~60 分钟刷新一次，这里取 55 分钟 */
  private static final long JWT_REFRESH_SECONDS = 3300;

  /** P-256 签名分量固定长度（字节） */
  private static final int EC_COMPONENT_LENGTH = 32;

  @Value("${moyuyo.push.apns.key-file:}")
  private String keyFile;

  @Value("${moyuyo.push.apns.key-id:}")
  private String keyId;

  @Value("${moyuyo.push.apns.team-id:}")
  private String teamId;

  @Value("${moyuyo.push.apns.bundle-id:}")
  private String bundleId;

  @Value("${moyuyo.push.apns.environment:prod}")
  private String environment;

  private HttpClient httpClient;
  private PrivateKey privateKey;
  private String host;

  private volatile boolean available;

  private volatile String cachedJwt;
  private volatile long jwtGeneratedAt;

  @PostConstruct
  void init() {
    try {
      if (keyFile == null || keyFile.isBlank()) {
        throw new IllegalStateException("moyuyo.push.apns.key-file 未配置");
      }
      if (keyId == null || keyId.isBlank() || teamId == null || teamId.isBlank()) {
        throw new IllegalStateException("APNs key-id / team-id 未配置");
      }
      if (bundleId == null || bundleId.isBlank()) {
        throw new IllegalStateException("APNs bundle-id 未配置");
      }
      privateKey = parsePrivateKey(new String(java.nio.file.Files.readAllBytes(new File(keyFile.trim()).toPath()),
          StandardCharsets.UTF_8));
      host = isSandbox() ? SANDBOX_HOST : PROD_HOST;
      httpClient = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
          .connectTimeout(Duration.ofSeconds(5))
          .build();
      available = true;
      log.info("[push] APNs 初始化成功: environment={}, bundleId={}", isSandbox() ? "sandbox" : "prod", bundleId);
    } catch (Exception e) {
      available = false;
      log.error("[push] APNs 初始化失败，iOS 推送将不可用: {}", e.getMessage());
    }
  }

  @Override
  public boolean supports(String platform) {
    return platform != null && platform.trim().toUpperCase().startsWith("IOS");
  }

  @Override
  public void send(String pushToken, String title, String body) {
    if (!available) {
      throw new IllegalStateException("APNs 未配置或初始化失败");
    }
    String token = normalizeToken(pushToken);
    if (token.isEmpty()) {
      throw new IllegalArgumentException("APNs deviceToken 为空");
    }
    try {
      HttpResponse<String> response = postMessage(getJwt(), token, title, body);
      if (response.statusCode() == 401 || response.statusCode() == 403) {
        // JWT 失效/过期：刷新后重试一次
        invalidateJwt();
        response = postMessage(getJwt(), token, title, body);
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "APNs 发送失败: HTTP " + response.statusCode() + " " + describeError(response.body()));
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("APNs 请求异常: " + e.getMessage(), e);
    }
  }

  /**
   * 发送一条 APNs 通知
   */
  private HttpResponse<String> postMessage(String jwt, String token, String title, String body) throws Exception {
    Map<String, Object> alert = new LinkedHashMap<>();
    alert.put("title", title == null ? "" : title);
    alert.put("body", body == null ? "" : body);

    Map<String, Object> aps = new LinkedHashMap<>();
    aps.put("alert", alert);
    aps.put("sound", "default");

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("aps", aps);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(host + "/3/device/" + token))
        .version(HttpClient.Version.HTTP_2)
        .timeout(Duration.ofSeconds(10))
        .header("authorization", "bearer " + jwt)
        .header("apns-topic", bundleId)
        .header("apns-push-type", "alert")
        .header("apns-priority", "10")
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(root), StandardCharsets.UTF_8))
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  /**
   * 获取 JWT（缓存 55 分钟）
   */
  private synchronized String getJwt() {
    long now = Instant.now().getEpochSecond();
    if (cachedJwt != null && now - jwtGeneratedAt < JWT_REFRESH_SECONDS) {
      return cachedJwt;
    }
    try {
      String jwt = buildJwt(now);
      cachedJwt = jwt;
      jwtGeneratedAt = now;
      return jwt;
    } catch (Exception e) {
      throw new IllegalStateException("APNs JWT 生成失败: " + e.getMessage(), e);
    }
  }

  private synchronized void invalidateJwt() {
    cachedJwt = null;
    jwtGeneratedAt = 0;
  }

  /**
   * 构造 ES256 JWT
   */
  private String buildJwt(long now) throws Exception {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "ES256");
    header.put("kid", keyId);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("iss", teamId);
    payload.put("iat", now);

    String signingInput = base64Url(JSON.writeValueAsBytes(header)) + "." + base64Url(JSON.writeValueAsBytes(payload));
    Signature signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(privateKey);
    signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
    return signingInput + "." + base64Url(derToRaw(signer.sign()));
  }

  /**
   * 解析 .p8 私钥（PKCS#8 PEM）
   */
  private PrivateKey parsePrivateKey(String pem) throws Exception {
    String normalized = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(normalized);
    KeyFactory keyFactory = KeyFactory.getInstance("EC");
    return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  /**
   * Java ECDSA 签名为 DER（SEQUENCE{INTEGER r, INTEGER s}），JWT ES256 要求 R||S 固定 64 字节
   */
  private byte[] derToRaw(byte[] der) {
    if (der.length < 2 || der[0] != 0x30) {
      throw new IllegalStateException("ECDSA 签名格式非法");
    }
    int offset = 2;
    if ((der[1] & 0x80) != 0) {
      // 长格式长度（P-256 签名一般不会触发，兼容处理）
      offset += der[1] & 0x7f;
    }
    int[] cursor = {offset};
    byte[] r = readDerInteger(der, cursor);
    byte[] s = readDerInteger(der, cursor);

    byte[] raw = new byte[EC_COMPONENT_LENGTH * 2];
    copyFixedLength(r, raw, 0);
    copyFixedLength(s, raw, EC_COMPONENT_LENGTH);
    return raw;
  }

  private byte[] readDerInteger(byte[] der, int[] cursor) {
    if (cursor[0] >= der.length || der[cursor[0]++] != 0x02) {
      throw new IllegalStateException("ECDSA 签名缺少 INTEGER");
    }
    int length = der[cursor[0]++];
    if ((length & 0x80) != 0) {
      int lengthBytes = length & 0x7f;
      length = 0;
      for (int i = 0; i < lengthBytes; i++) {
        length = (length << 8) | (der[cursor[0]++] & 0xff);
      }
    }
    byte[] value = new byte[length];
    System.arraycopy(der, cursor[0], value, 0, length);
    cursor[0] += length;
    return value;
  }

  /**
   * 去掉前导 0x00 并按固定 32 字节右对齐写入
   */
  private void copyFixedLength(byte[] value, byte[] target, int offset) {
    int start = 0;
    while (start < value.length - 1 && value[start] == 0) {
      start++;
    }
    int length = value.length - start;
    if (length > EC_COMPONENT_LENGTH) {
      start += length - EC_COMPONENT_LENGTH;
      length = EC_COMPONENT_LENGTH;
    }
    System.arraycopy(value, start, target, offset + EC_COMPONENT_LENGTH - length, length);
  }

  /**
   * 去掉 iOS 端可能带上的尖括号/空格（NSData.description 形式）
   */
  private String normalizeToken(String pushToken) {
    if (pushToken == null) {
      return "";
    }
    return pushToken.replace("<", "").replace(">", "").replace(" ", "").trim();
  }

  private boolean isSandbox() {
    String value = environment == null ? "" : environment.trim().toLowerCase();
    return value.startsWith("dev") || value.startsWith("sandbox");
  }

  /**
   * 提取 APNs 错误响应中的 reason 字段
   */
  private String describeError(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    try {
      JsonNode json = JSON.readTree(body);
      String reason = json.path("reason").asText("");
      return reason.isBlank() ? abbreviate(body) : reason;
    } catch (Exception e) {
      return abbreviate(body);
    }
  }

  private String abbreviate(String text) {
    return text.length() <= 300 ? text : text.substring(0, 300) + "...";
  }

  private String base64Url(byte[] data) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
  }
}

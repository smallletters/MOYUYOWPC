package com.moyuyo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FcmAppPushServiceImpl 单元测试（不发起真实网络请求）。
 * <p>
 * 覆盖：
 * 1) 未配置服务账号文件时 send 明确失败，不伪造成功
 * 2) 服务账号初始化成功，JWT assertion 为 RS256 且可被公钥验签
 */
class FcmAppPushServiceImplTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  @DisplayName("未配置服务账号文件：send 明确失败")
  void sendWithoutConfigShouldFail() {
    FcmAppPushServiceImpl service = new FcmAppPushServiceImpl();
    ReflectionTestUtils.setField(service, "serviceAccountFile", "");
    service.init();

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> service.send("fcm-token", "标题", "正文"));
    assertEquals("FCM 未配置或初始化失败", error.getMessage());
  }

  @Test
  @DisplayName("服务账号初始化成功：JWT 为 RS256 且可验签")
  void buildAssertionShouldBeVerifiable(@TempDir Path tempDir) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();

    String pem = "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(keyPair.getPrivate().getEncoded())
        + "\n-----END PRIVATE KEY-----\n";

    Map<String, Object> account = new LinkedHashMap<>();
    account.put("project_id", "moyuyo-test");
    account.put("client_email", "firebase-adminsdk@moyuyo-test.iam.gserviceaccount.com");
    account.put("private_key", pem);
    Path accountFile = tempDir.resolve("service-account.json");
    Files.writeString(accountFile, JSON.writeValueAsString(account), StandardCharsets.UTF_8);

    FcmAppPushServiceImpl service = new FcmAppPushServiceImpl();
    ReflectionTestUtils.setField(service, "serviceAccountFile", accountFile.toString());
    service.init();

    long now = 1_700_000_000L;
    String assertion = ReflectionTestUtils.invokeMethod(service, "buildAssertion", now);
    String[] parts = assertion.split("\\.");
    assertEquals(3, parts.length, "JWT 必须由 header.payload.signature 三段组成");

    String signingInput = parts[0] + "." + parts[1];
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
    assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])), "签名应可通过公钥验证");

    JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
    assertEquals("RS256", header.path("alg").asText());
    JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
    assertEquals("firebase-adminsdk@moyuyo-test.iam.gserviceaccount.com", payload.path("iss").asText());
    assertEquals("https://oauth2.googleapis.com/token", payload.path("aud").asText());
    assertEquals(now + 3600, payload.path("exp").asLong());
  }
}

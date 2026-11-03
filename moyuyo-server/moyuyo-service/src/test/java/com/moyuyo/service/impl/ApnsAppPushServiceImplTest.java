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
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApnsAppPushServiceImpl 单元测试（不发起真实网络请求）。
 * <p>
 * 覆盖：
 * 1) 未配置 p8 密钥时 send 明确失败
 * 2) JWT 为 ES256，且 DER→raw 转换后的签名可被公钥验签
 */
class ApnsAppPushServiceImplTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  @DisplayName("未配置 APNs 密钥：send 明确失败")
  void sendWithoutConfigShouldFail() {
    ApnsAppPushServiceImpl service = new ApnsAppPushServiceImpl();
    ReflectionTestUtils.setField(service, "keyFile", "");
    service.init();

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> service.send("a1b2c3", "标题", "正文"));
    assertEquals("APNs 未配置或初始化失败", error.getMessage());
  }

  @Test
  @DisplayName("APNs JWT：ES256 且签名可验签")
  void buildJwtShouldBeVerifiable(@TempDir Path tempDir) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair keyPair = generator.generateKeyPair();

    String pem = "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(keyPair.getPrivate().getEncoded())
        + "\n-----END PRIVATE KEY-----\n";
    Path keyFile = tempDir.resolve("AuthKey_TEST.p8");
    Files.writeString(keyFile, pem, StandardCharsets.UTF_8);

    ApnsAppPushServiceImpl service = new ApnsAppPushServiceImpl();
    ReflectionTestUtils.setField(service, "keyFile", keyFile.toString());
    ReflectionTestUtils.setField(service, "keyId", "TESTKEYID");
    ReflectionTestUtils.setField(service, "teamId", "TESTTEAMID");
    ReflectionTestUtils.setField(service, "bundleId", "com.moyuyo.test");
    ReflectionTestUtils.setField(service, "environment", "prod");
    service.init();

    long now = 1_700_000_000L;
    String jwt = ReflectionTestUtils.invokeMethod(service, "buildJwt", now);
    String[] parts = jwt.split("\\.");
    assertEquals(3, parts.length, "JWT 必须由 header.payload.signature 三段组成");

    String signingInput = parts[0] + "." + parts[1];
    Signature verifier = Signature.getInstance("SHA256withECDSA");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
    assertTrue(verifier.verify(rawToDer(Base64.getUrlDecoder().decode(parts[2]))),
        "APNs JWT 签名应可通过 EC 公钥验证（R||S raw 格式）");

    JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
    assertEquals("ES256", header.path("alg").asText());
    assertEquals("TESTKEYID", header.path("kid").asText());
    JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
    assertEquals("TESTTEAMID", payload.path("iss").asText());
    assertEquals(now, payload.path("iat").asLong());
  }

  /**
   * JWT ES256 的签名为 R||S（各 32 字节），Java verify 需要 DER 编码，测试内重建 DER
   */
  private byte[] rawToDer(byte[] raw) {
    assertEquals(64, raw.length, "P-256 签名应为 32+32 字节");
    byte[] r = encodeInteger(Arrays.copyOfRange(raw, 0, 32));
    byte[] s = encodeInteger(Arrays.copyOfRange(raw, 32, 64));
    int total = 2 + r.length + 2 + s.length;
    byte[] der = new byte[2 + total];
    der[0] = 0x30;
    der[1] = (byte) total;
    int index = 2;
    der[index++] = 0x02;
    der[index++] = (byte) r.length;
    System.arraycopy(r, 0, der, index, r.length);
    index += r.length;
    der[index++] = 0x02;
    der[index++] = (byte) s.length;
    System.arraycopy(s, 0, der, index, s.length);
    return der;
  }

  private byte[] encodeInteger(byte[] value) {
    int start = 0;
    while (start < value.length - 1 && value[start] == 0) {
      start++;
    }
    byte[] trimmed = Arrays.copyOfRange(value, start, value.length);
    if ((trimmed[0] & 0x80) != 0) {
      byte[] padded = new byte[trimmed.length + 1];
      System.arraycopy(trimmed, 0, padded, 1, trimmed.length);
      return padded;
    }
    return trimmed;
  }
}

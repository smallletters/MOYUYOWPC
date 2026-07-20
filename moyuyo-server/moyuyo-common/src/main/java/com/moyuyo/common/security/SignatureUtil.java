package com.moyuyo.common.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
public class SignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public static String generate(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("签名生成失败", e);
            throw new RuntimeException("签名生成失败", e);
        }
    }

    public static boolean verify(String payload, String secret, String expectedSignature) {
        String actual = generate(payload, secret);
        return actual.equals(expectedSignature);
    }
}

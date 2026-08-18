package com.moyuyo.common.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * API 签名工具类（HMAC-SHA256 + Base64）
 * <p>
 * 关键修复：verify 必须对客户端传入的签名做 Base64 解码后与 HMAC 原始字节做常量时间比较。
 * 之前的实现直接比较 Base64 字符串与原始字节会导致签名校验 100% 失败（任何合法签名都被拒）。
 */
@Slf4j
public class SignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 生成签名：HMAC-SHA256(payload, secret) → Base64
     */
    public static String generate(String payload, String secret) {
        if (payload == null || secret == null) {
            throw new IllegalArgumentException("payload 与 secret 不能为 null");
        }
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

    /**
     * 校验签名：客户端传入 Base64 字符串 → 解码为原始字节 → 常量时间比较
     * <p>
     * 安全要点：
     * 1. 必须 Base64 解码客户端签名后再与 HMAC 原始字节比较，否则永远是 false
     * 2. 使用 MessageDigest.isEqual 做常量时间比较，防止时序攻击
     * 3. 解码失败时直接返回 false（视为不合法），避免异常泄露到上游
     * 4. 长度不匹配时直接返回 false，避免数组越界
     */
    public static boolean verify(String payload, String secret, String expectedBase64Signature) {
        if (payload == null || secret == null || expectedBase64Signature == null) {
            return false;
        }
        // 计算期望的原始 HMAC 字节
        byte[] expected;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("签名计算失败", e);
            return false;
        }

        // 解码客户端传入的 Base64 签名
        byte[] provided;
        try {
            provided = Base64.getDecoder().decode(expectedBase64Signature);
        } catch (IllegalArgumentException e) {
            // 客户端传入的不是合法 Base64，视为签名不合法
            return false;
        }

        // 长度不一致直接返回 false，避免 MessageDigest.isEqual 在不等长数组上的行为歧义
        if (expected.length != provided.length) {
            return false;
        }

        // 常量时间比较，防止时序攻击
        return MessageDigest.isEqual(expected, provided);
    }
}
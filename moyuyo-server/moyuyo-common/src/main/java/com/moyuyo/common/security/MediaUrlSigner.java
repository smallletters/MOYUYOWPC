package com.moyuyo.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 私信媒体（图片/语音/文件）访问签名工具。
 * <p>
 * 背景：私信附件落在 {@code /uploads/dm/**}，该目录由 WebMvcConfig 映射为静态资源，
 * 不受 JwtAuthFilter（只拦截 /api/*）保护。若完全公开，任何拿到 URL 的人都能读取他人聊天内容。
 * <p>
 * 方案：消息接口返回时由服务端为 dm 路径追加 {@code ?e=<过期秒>&s=<HMAC>} 签名，
 * 静态资源过滤器校验签名后才放行。签名只校验"服务端签发过"，不落库、不影响历史数据。
 */
@Component
public class MediaUrlSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 仅私信附件目录需要签名；商品素材图等公开图片保持原行为 */
    public static final String PROTECTED_PREFIX = "/uploads/dm/";

    private final byte[] secret;
    private final long ttlSeconds;

    public MediaUrlSigner(
            @Value("${moyuyo.upload.media-sign-secret:}") String configuredSecret,
            @Value("${moyuyo.upload.media-sign-ttl-seconds:21600}") long ttlSeconds) {
        this.secret = resolveSecret(configuredSecret);
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 21600L;
    }

    /**
     * 解析签名密钥：
     * - 未配置：退化为进程级随机密钥（仅适合本地/单实例；生产由 ProdConfigValidator 强制必填）
     * - 已配置但短于 32 位：直接抛异常，避免"配了弱密钥等于没配"导致签名可被暴力伪造
     */
    private static byte[] resolveSecret(String configured) {
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        }
        String trimmed = configured.trim();
        if (trimmed.length() < 32) {
            throw new IllegalStateException(
                    "moyuyo.upload.media-sign-secret 长度必须 ≥32 位，避免弱密钥导致私信媒体签名可伪造");
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    /** 判断路径是否属于需要签名的私信媒体 */
    public boolean isProtectedPath(String path) {
        return path != null && path.startsWith(PROTECTED_PREFIX);
    }

    /**
     * 为私信媒体 URL 追加签名参数；非私信路径原样返回。
     * 重复调用会先剥离旧参数再重新签名，避免参数叠加。
     */
    public String sign(String url) {
        if (!isProtectedPath(url)) {
            return url;
        }
        String path = stripQuery(url);
        long expiresAt = System.currentTimeMillis() / 1000 + ttlSeconds;
        return path + "?e=" + expiresAt + "&s=" + hmac(path, expiresAt);
    }

    /** 校验签名：路径、过期时间、HMAC 三者同时匹配才放行（常量时间比较防时序攻击） */
    public boolean verify(String path, String expiresAt, String signature) {
        if (!isProtectedPath(path) || expiresAt == null || signature == null) {
            return false;
        }
        long expires;
        try {
            expires = Long.parseLong(expiresAt.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        // 过期秒数必须大于当前时间
        if (expires < System.currentTimeMillis() / 1000) {
            return false;
        }
        String expected = hmac(stripQuery(path), expires);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String path, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((path + "\n" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("私信媒体签名失败", e);
        }
    }

    /** 去掉已有 query，保证签名计算基于纯路径 */
    private String stripQuery(String url) {
        int index = url.indexOf('?');
        return index >= 0 ? url.substring(0, index) : url;
    }
}

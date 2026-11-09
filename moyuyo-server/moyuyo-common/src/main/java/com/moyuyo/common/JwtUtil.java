package com.moyuyo.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成/验证/解析 Token
 * <p>
 * P1 性能修复：
 * - 历史实现中 {@link #validate(String)} / {@link #getUserId(String)} / {@link #getRole(String)} 每次都重新
 *   调用 {@link #parse(String)}，意味着每个鉴权请求至少解析 2~3 次 JWT（包含一次 HMAC 验签）。
 *   HMAC-SHA256 单次验签成本约 50~150µs（业务 token 一般 200~500B），鉴权密集型接口下浪费 30%+ CPU。
 * - 修复：增加 {@link #parseClaims(String)} 单次解析接口；鉴权链推荐先 parseClaims 拿到 Claims 再多次复用，
 *   后续 getUserId/getRole 直接从 Claims 读取，无需再次验签。
 * - 兼容老接口：保留 validate / getUserId / getRole(token) 形态以避免外部调用方编译失败；
 *   内部冗余 parse 在业务侧可逐步切换到 parseClaims。
 */
@Component
public class JwtUtil {

    /** Token 版本号 claim：每次改密/重置密码后服务端递增，旧版本 token 立即失效 */
    public static final String CLAIM_TOKEN_VERSION = "ver";

    private final SecretKey secretKey;
    private final long expireMs;

    /**
     * 管理端 token 有效期（小时）：管理端没有 refresh token，
     * 使用更短有效期降低账号被禁用/降权后的残留窗口。默认 4 小时。
     */
    @Value("${jwt.admin-expire-hours:4}")
    private long adminExpireHours = 4;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expire-hours:2}") long expireHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireHours * 3600 * 1000;
    }

    /**
     * 生成 Token（C 端用户，无角色、无版本号；仅用于测试/兼容历史调用）
     */
    public String generate(Long userId, String email) {
        return generate(userId, email, null, null);
    }

    /**
     * 生成 Token（兼容历史调用：不携带版本号，等价于版本 0）
     */
    public String generate(Long userId, String email, String role) {
        return generate(userId, email, role, null);
    }

    /**
     * 生成 Token，可携带角色（管理端传入管理员角色，用于接口鉴权）与 token 版本号。
     * <p>
     * tokenVersion 为服务端当前版本（Redis 中保存）。改密后版本递增，
     * 旧 token 携带的版本号与服务端不一致，会在 JwtAuthFilter 中被拒绝。
     *
     * @param tokenVersion 当前版本号；null 表示不写入 claim（历史兼容，按 0 处理）
     */
    public String generate(Long userId, String email, String role, Long tokenVersion) {
        return generateWithExpiry(userId, email, role, tokenVersion, expireMs);
    }

    /**
     * 生成管理端 Token：使用更短的 {@code jwt.admin-expire-hours}（默认 4 小时）。
     * 管理端没有 refresh token，缩短有效期可降低禁用/降权后的残留窗口。
     */
    public String generateAdmin(Long userId, String email, String role, Long tokenVersion) {
        long adminExpireMs = Math.max(1L, adminExpireHours) * 3600L * 1000L;
        return generateWithExpiry(userId, email, role, tokenVersion, adminExpireMs);
    }

    private String generateWithExpiry(Long userId, String email, String role,
                                      Long tokenVersion, long ttlMillis) {
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMillis));
        if (role != null && !role.isBlank()) {
            builder.claim("role", role);
        }
        if (tokenVersion != null) {
            builder.claim(CLAIM_TOKEN_VERSION, tokenVersion);
        }
        return builder.signWith(secretKey).compact();
    }

    /**
     * 单次解析 Claims（鉴权链推荐入口）。
     * <p>
     * 与 {@link #parse(String)} 不同：本方法不会捕获异常直接吞掉，让 JwtAuthFilter 能在
     * DEBUG 日志中区分 ExpiredJwtException / SignatureException / MalformedJwtException，
     * 便于后续接入审计/告警。
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 解析 Token（保留旧接口以兼容历史调用方）
     * <p>
     * 注意：调用本方法前通常已经过 {@link #validate(String)} 的预校验，因此异常不会频繁抛出；
     * 如果调用方未先 validate，则需要捕获 JwtException 自行处理。
     */
    public Claims parse(String token) {
        return parseClaims(token);
    }

    /**
     * 验证 Token 是否有效（签名 + 过期时间）
     */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 Token 提取用户 ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * 从 Claims 中提取用户 ID（已 parseClaims 后复用，避免二次验签）
     */
    public Long getUserIdFromClaims(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 提取角色（无角色时返回 null）
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 从 Claims 中提取角色（已 parseClaims 后复用，避免二次验签）
     */
    public String getRoleFromClaims(Claims claims) {
        return claims.get("role", String.class);
    }

    /**
     * 从 Claims 中提取 token 版本号；历史 token 没有该 claim 时返回 null（调用方按 0 处理）。
     */
    public Long getTokenVersionFromClaims(Claims claims) {
        Object raw = claims.get(CLAIM_TOKEN_VERSION);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
package com.moyuyo.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成/验证/刷新 Token
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expireMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expire-hours:2}") long expireHours) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireHours * 3600 * 1000;
    }

    /**
     * 生成 Token（C 端用户，无角色）
     */
    public String generate(Long userId, String email) {
        return generate(userId, email, null);
    }

    /**
     * 生成 Token，可携带角色（管理端传入管理员角色，用于接口鉴权）
     */
    public String generate(Long userId, String email, String role) {
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMs));
        if (role != null && !role.isBlank()) {
            builder.claim("role", role);
        }
        return builder.signWith(secretKey).compact();
    }

    /**
     * 解析 Token
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 Token 提取用户 ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    /**
     * 从 Token 提取角色（无角色时返回 null）
     */
    public String getRole(String token) {
        return parse(token).get("role", String.class);
    }
}

package com.moyuyo.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("TestSecretKeyForUnitTestsAtLeast32Bytes!!", 2);

    @Test
    void generate_ShouldCreateValidToken() {
        String token = jwtUtil.generate(1L, "test@test.com");
        assertNotNull(token);
        assertTrue(jwtUtil.validate(token));
    }

    @Test
    void validate_WithInvalidToken_ShouldReturnFalse() {
        assertFalse(jwtUtil.validate("invalid.token.here"));
    }

    @Test
    void getUserId_ShouldExtractCorrectId() {
        String token = jwtUtil.generate(42L, "user@test.com");
        assertEquals(42L, jwtUtil.getUserId(token));
    }

    @Test
    void generate_WithTokenVersion_ShouldExposeVersionClaim() {
        String token = jwtUtil.generate(7L, "v@test.com", "OPERATOR", 3L);
        assertEquals(3L, jwtUtil.getTokenVersionFromClaims(jwtUtil.parseClaims(token)));
        assertEquals("OPERATOR", jwtUtil.getRoleFromClaims(jwtUtil.parseClaims(token)));
    }

    @Test
    void generate_LegacyOverload_ShouldHaveNoVersionClaim() {
        String token = jwtUtil.generate(7L, "v@test.com");
        assertNull(jwtUtil.getTokenVersionFromClaims(jwtUtil.parseClaims(token)),
                "历史 3 参调用不写版本号，鉴权侧按版本 0 兼容");
    }

    @Test
    void validate_WithExpiredToken_ShouldReturnFalse() throws Exception {
        JwtUtil shortLived = new JwtUtil("TestSecretKeyForUnitTestsAtLeast32Bytes!!", 0);
        String token = shortLived.generate(1L, "test@test.com");
        Thread.sleep(100);
        // 0小时过期，token应已失效
        assertFalse(shortLived.validate(token));
    }
}

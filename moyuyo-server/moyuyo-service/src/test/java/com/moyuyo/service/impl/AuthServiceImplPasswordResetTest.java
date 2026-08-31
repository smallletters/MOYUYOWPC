package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.dto.auth.EmailVerifyRequest;
import com.moyuyo.common.dto.auth.ResetPasswordRequest;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.SmsCodeMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AuthService;
import com.moyuyo.service.EmailService;
import com.moyuyo.service.SmsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 忘记密码链路单元测试（纯 Mock Redis/DB/EmailService）。
 * 覆盖：
 * 1) sendPasswordReset：6 位验证码 + Redis 双 key + TTL=1800s + EmailService 调 sendPasswordResetCode(30min)
 * 2) sendPasswordReset：未注册邮箱静默返回（防用户名枚举）
 * 3) resetPassword：正确 code → 落 password_hash + 一次性消费双 key
 * 4) resetPassword：code miss vs user miss 同一错误（防用户名枚举）
 * 5) resetPassword：失败计数 ≥ 5 → 主动清 key + 抛"验证码错误次数过多"
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplPasswordResetTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SmsCodeMapper smsCodeMapper;
    @Mock
    private ObjectProvider<SmsService> smsServiceProvider;
    @Mock
    private ObjectProvider<EmailService> emailServiceProvider;
    @Mock
    private EmailService emailService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AuthService authService;

    @Captor
    private ArgumentCaptor<String> redisKeyCaptor;
    @Captor
    private ArgumentCaptor<String> redisValueCaptor;
    @Captor
    private ArgumentCaptor<Long> ttlCaptor;
    @Captor
    private ArgumentCaptor<TimeUnit> ttlUnitCaptor;
    @Captor
    private ArgumentCaptor<String> codeCaptor;
    @Captor
    private ArgumentCaptor<UserEntity> userCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(emailServiceProvider.getIfAvailable()).thenReturn(emailService);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        authService = new AuthServiceImpl(userMapper, smsCodeMapper,
                smsServiceProvider, emailServiceProvider,
                jwtUtil, redisTemplate, passwordEncoder, meterRegistry);
    }

    @Test
    @DisplayName("sendPasswordReset：未注册邮箱静默返回 200，不写 Redis 不发邮件（防用户名枚举）")
    void sendPasswordReset_nonexistentUser_silentReturn() {
        EmailVerifyRequest req = new EmailVerifyRequest();
        req.setEmail("ghost@example.com");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertDoesNotThrow(() -> authService.sendPasswordReset(req));

        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(emailService, never()).sendPasswordResetCode(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("sendPasswordReset：生成 6 位验证码 + 2 个 Redis key + TTL=1800s + 调 EmailService(30min)")
    void sendPasswordReset_existingUser_writesRedisAndCallsEmail() {
        EmailVerifyRequest req = new EmailVerifyRequest();
        req.setEmail("  MixedCase@Example.COM  ");
        UserEntity user = new UserEntity();
        user.setId(99L);
        user.setEmail("mixedcase@example.com");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        authService.sendPasswordReset(req);

        verify(valueOps, times(2)).set(
                redisKeyCaptor.capture(),
                redisValueCaptor.capture(),
                ttlCaptor.capture(),
                ttlUnitCaptor.capture());

        ttlCaptor.getAllValues().forEach(ttl -> assertEquals(1800L, ttl,
                "重置验证码及失败计数 key 的 TTL 必须为 1800 秒（30 分钟）"));
        ttlUnitCaptor.getAllValues().forEach(unit -> assertEquals(TimeUnit.SECONDS, unit));
        List<String> values = redisValueCaptor.getAllValues();
        assertTrue(values.contains("mixedcase@example.com"),
                "normalizeEmail 后邮箱应为 trim+小写，DB 查询与 Redis 存储要一致");
        assertTrue(values.contains("0"),
                "失败计数 key 的初始值必须为 0（字符串），后续 resetPassword 防爆破分支依赖");

        List<String> keys = redisKeyCaptor.getAllValues();
        String code = extractResetCodeFromKeys(keys);
        assertNotNull(code, "两个 Redis key 后缀应该是同一段 6 位数字 code");
        assertTrue(code.matches("^\\d{6}$"), "生成的重置验证码必须是 6 位数字，与前端 forgot.vue 6-digit input 对齐");
        assertTrue(keys.contains("auth:resetcode:" + code),
                "验证码 key 必须以 auth:resetcode: 为前缀");
        assertTrue(keys.contains("auth:resetcode-fail:" + code),
                "失败计数 key 必须以 auth:resetcode-fail: 为前缀");

        verify(emailService).sendPasswordResetCode(eq("mixedcase@example.com"), codeCaptor.capture(), eq(30));
        assertEquals(code, codeCaptor.getValue(),
                "EmailService.sendPasswordResetCode 传入的 code 必须与 Redis 写入的 code 完全一致");
    }

    @Test
    @DisplayName("resetPassword：正确 6 位 code → 更新 password_hash 且一次性删 2 个 Redis key")
    void resetPassword_validCode_updatesPasswordAndConsumesKey() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(valueOps.get("auth:resetcode:123456")).thenReturn("alice@example.com");
        when(valueOps.get("auth:resetcode-fail:123456")).thenReturn("0");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.encode("NewPass1234")).thenReturn("$encoded$NewPass1234");

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("123456");
        req.setNewPassword("NewPass1234");

        assertDoesNotThrow(() -> authService.resetPassword(req));

        verify(passwordEncoder).encode("NewPass1234");
        verify(userMapper).updateById(userCaptor.capture());
        assertEquals("$encoded$NewPass1234", userCaptor.getValue().getPasswordHash(),
                "落库的 passwordHash 必须来自 passwordEncoder.encode，禁止明文");
        verify(redisTemplate).delete("auth:resetcode:123456");
        verify(redisTemplate).delete("auth:resetcode-fail:123456");
    }

    @Test
    @DisplayName("resetPassword：code 不存在 vs 邮箱不存在 → 同一错误消息（防用户名枚举）")
    void resetPassword_codeMissingOrUserMissing_sameErrorMessage() {
        ResetPasswordRequest req1 = new ResetPasswordRequest();
        req1.setToken("000000");
        req1.setNewPassword("NewPass1234");
        when(valueOps.get("auth:resetcode:000000")).thenReturn(null);
        IllegalArgumentException err1 = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(req1));
        assertEquals("Invalid or expired reset code", err1.getMessage());

        ResetPasswordRequest req2 = new ResetPasswordRequest();
        req2.setToken("111111");
        req2.setNewPassword("NewPass1234");
        when(valueOps.get("auth:resetcode:111111")).thenReturn("ghost@example.com");
        when(valueOps.get("auth:resetcode-fail:111111")).thenReturn("0");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        IllegalArgumentException err2 = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(req2));
        assertEquals("Invalid or expired reset code", err2.getMessage(),
                "code miss vs user miss 必须返回完全相同的错误消息，防止攻击者枚举出哪些邮箱已注册");
    }

    @Test
    @DisplayName("resetPassword：失败计数 ≥ 5 → 立刻失效并主动删除双 key")
    void resetPassword_failCountExceedsMax_cleanupAndThrow() {
        when(valueOps.get("auth:resetcode:999999")).thenReturn("bob@example.com");
        when(valueOps.get("auth:resetcode-fail:999999")).thenReturn("5");

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("999999");
        req.setNewPassword("NewPass1234");

        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(req));
        assertEquals("验证码错误次数过多，请重新获取", err.getMessage(),
                "失败次数达到 5 次后，必须强制失效并提示重新获取，防止 6 位数字被暴力穷举");
        verify(redisTemplate).delete("auth:resetcode:999999");
        verify(redisTemplate).delete("auth:resetcode-fail:999999");
        verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    private String extractResetCodeFromKeys(List<String> keys) {
        String codeKey = keys.stream().filter(k -> k.startsWith("auth:resetcode:")).findFirst().orElse(null);
        String failKey = keys.stream().filter(k -> k.startsWith("auth:resetcode-fail:")).findFirst().orElse(null);
        if (codeKey == null || failKey == null) return null;
        String codeA = codeKey.substring("auth:resetcode:".length());
        String codeB = failKey.substring("auth:resetcode-fail:".length());
        return codeA.equals(codeB) ? codeA : null;
    }
}
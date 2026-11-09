package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.dto.auth.EmailVerifyRequest;
import com.moyuyo.common.dto.auth.ResetPasswordRequest;
import com.moyuyo.dao.admin.mapper.DataExportRequestMapper;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.SmsCodeMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AuthService;
import com.moyuyo.service.EmailService;
import com.moyuyo.service.InviteService;
import com.moyuyo.service.SmsService;
import com.moyuyo.service.event.UserTokenInvalidatedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
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
 * 1) sendPasswordReset：6 位验证码与邮箱绑定 + Redis 双 key + TTL=1800s + EmailService 调 sendPasswordResetCode(30min)
 * 2) sendPasswordReset：未注册邮箱静默返回（防用户名枚举）
 * 3) resetPassword：email + 正确 code → 落 password_hash + 一次性消费 key + 吊销 refresh token
 * 4) resetPassword：code miss vs 邮箱/用户 miss 同一错误（防用户名枚举）
 * 5) resetPassword：失败计数 ≥ 5 → 主动清 key + 抛"验证码错误次数过多"
 * 6) resetPassword：code 不匹配必须 INCR 失败计数（防 6 位码穷举）
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AuthServiceImplPasswordResetTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SmsCodeMapper smsCodeMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private DataExportRequestMapper dataExportRequestMapper;
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
    private InviteService inviteService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

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
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(setOps.members(anyString())).thenReturn(Collections.emptySet());
        lenient().when(emailServiceProvider.getIfAvailable()).thenReturn(emailService);
        // 发送冷却 SETNX 默认视为首次发送
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        authService = new AuthServiceImpl(userMapper, smsCodeMapper,
                orderMapper, dataExportRequestMapper,
                smsServiceProvider, emailServiceProvider,
                jwtUtil, redisTemplate, passwordEncoder, inviteService, eventPublisher, meterRegistry);
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
    @DisplayName("sendPasswordReset：生成 6 位验证码，key 与邮箱绑定，TTL=1800s")
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

        String normalizedEmail = "mixedcase@example.com";
        List<String> keys = redisKeyCaptor.getAllValues();
        List<String> values = redisValueCaptor.getAllValues();
        assertTrue(keys.contains("auth:resetcode:" + normalizedEmail),
                "验证码 key 必须以邮箱为后缀，避免全局 code key 被撞码覆盖");
        assertTrue(keys.contains("auth:resetcode-fail:" + normalizedEmail),
                "失败计数 key 必须以邮箱为后缀");
        assertTrue(values.contains("0"), "失败计数初始值必须为 0");

        String code = values.stream()
                .filter(v -> v.matches("^\\d{6}$"))
                .findFirst().orElse(null);
        assertNotNull(code, "必须写入一个 6 位数字验证码");
        assertTrue(values.contains(code), "验证码必须写入 Redis");

        verify(emailService).sendPasswordResetCode(eq(normalizedEmail), codeCaptor.capture(), eq(30));
        assertEquals(code, codeCaptor.getValue(),
                "EmailService.sendPasswordResetCode 传入的 code 必须与 Redis 写入的 code 完全一致");
    }

    @Test
    @DisplayName("resetPassword：email + 正确 code → 更新 password_hash 且一次性删 key")
    void resetPassword_validCode_updatesPasswordAndConsumesKey() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(valueOps.get("auth:resetcode:alice@example.com")).thenReturn("123456");
        when(valueOps.get("auth:resetcode-fail:alice@example.com")).thenReturn("0");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.encode("NewPass1234")).thenReturn("$encoded$NewPass1234");

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("alice@example.com");
        req.setToken("123456");
        req.setNewPassword("NewPass1234");

        assertDoesNotThrow(() -> authService.resetPassword(req));

        verify(passwordEncoder).encode("NewPass1234");
        verify(userMapper).updateById(userCaptor.capture());
        assertEquals("$encoded$NewPass1234", userCaptor.getValue().getPasswordHash(),
                "落库的 passwordHash 必须来自 passwordEncoder.encode，禁止明文");
        verify(redisTemplate).delete("auth:resetcode:alice@example.com");
        verify(redisTemplate).delete("auth:resetcode-fail:alice@example.com");
        verify(redisTemplate).delete("auth:resetcode-cooldown:alice@example.com");
        // 改密成功后必须递增 token 版本号，旧 access token / refresh token 立即失效
        verify(valueOps).increment("auth:token-version:1");
        // 同时发布 token 失效事件，供 api 模块强制关闭该用户的 WebSocket 会话
        verify(eventPublisher).publishEvent(any(UserTokenInvalidatedEvent.class));
    }

    @Test
    @DisplayName("resetPassword：code 不存在 vs 邮箱不存在 → 同一错误消息（防用户名枚举）")
    void resetPassword_codeMissingOrUserMissing_sameErrorMessage() {
        ResetPasswordRequest req1 = new ResetPasswordRequest();
        req1.setEmail("nobody@example.com");
        req1.setToken("000000");
        req1.setNewPassword("NewPass1234");
        when(valueOps.get("auth:resetcode:nobody@example.com")).thenReturn(null);
        when(valueOps.get("auth:resetcode-fail:nobody@example.com")).thenReturn("0");
        IllegalArgumentException err1 = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(req1));
        assertEquals("Invalid or expired reset code", err1.getMessage());

        ResetPasswordRequest req2 = new ResetPasswordRequest();
        req2.setEmail("ghost@example.com");
        req2.setToken("111111");
        req2.setNewPassword("NewPass1234");
        when(valueOps.get("auth:resetcode:ghost@example.com")).thenReturn("111111");
        when(valueOps.get("auth:resetcode-fail:ghost@example.com")).thenReturn("0");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        IllegalArgumentException err2 = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(req2));
        assertEquals("Invalid or expired reset code", err2.getMessage(),
                "code miss vs user miss 必须返回完全相同的错误消息，防止攻击者枚举出哪些邮箱已注册");
    }

    @Test
    @DisplayName("resetPassword：失败计数 ≥ 5 → 立刻失效并主动删除 key")
    void resetPassword_failCountExceedsMax_cleanupAndThrow() {
        when(valueOps.get("auth:resetcode:bob@example.com")).thenReturn("123456");
        when(valueOps.get("auth:resetcode-fail:bob@example.com")).thenReturn("5");

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("bob@example.com");
        req.setToken("999999");
        req.setNewPassword("NewPass1234");

        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(req));
        assertEquals("Invalid or expired reset code", err.getMessage(),
                "失败次数达到 5 次后必须强制失效；文案与验证码不存在保持一致，避免邮箱枚举");
        verify(redisTemplate).delete("auth:resetcode:bob@example.com");
        verify(redisTemplate).delete("auth:resetcode-fail:bob@example.com");
        verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("resetPassword：code 不匹配 → INCR 失败计数（防 6 位码穷举）")
    void resetPassword_wrongCode_incrementsFailCount() {
        when(valueOps.get("auth:resetcode:carol@example.com")).thenReturn("123456");
        when(valueOps.get("auth:resetcode-fail:carol@example.com")).thenReturn("2");
        when(valueOps.increment("auth:resetcode-fail:carol@example.com")).thenReturn(3L);

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("carol@example.com");
        req.setToken("654321");
        req.setNewPassword("NewPass1234");

        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(req));
        assertEquals("Invalid or expired reset code", err.getMessage(),
                "失败文案必须与 code 不存在一致，避免形成邮箱注册枚举 oracle");
        verify(valueOps).increment("auth:resetcode-fail:carol@example.com");
        verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}

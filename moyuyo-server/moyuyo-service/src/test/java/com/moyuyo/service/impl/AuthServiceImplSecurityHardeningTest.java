package com.moyuyo.service.impl;

import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.dto.auth.EmailVerifyConfirmRequest;
import com.moyuyo.common.dto.auth.LoginRequest;
import com.moyuyo.common.exception.BusinessException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-1/M-2 安全加固测试：2FA 登录强制校验、邮箱验证码失败计数。
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AuthServiceImplSecurityHardeningTest {

    @Mock private UserMapper userMapper;
    @Mock private SmsCodeMapper smsCodeMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private DataExportRequestMapper dataExportRequestMapper;
    @Mock private ObjectProvider<SmsService> smsServiceProvider;
    @Mock private ObjectProvider<EmailService> emailServiceProvider;
    @Mock private EmailService emailService;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private InviteService inviteService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SetOperations<String, String> setOps;

    private AuthService authService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(setOps.members(anyString())).thenReturn(java.util.Collections.emptySet());
        lenient().when(emailServiceProvider.getIfAvailable()).thenReturn(emailService);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        // 发送冷却默认视为首次发送（否则 Mockito 对 Boolean 默认返回 false 会提前 return）
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        user = new UserEntity();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPasswordHash("$hash");
        user.setStatus(1);
        user.setTwoFactorEnabled(true);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        authService = new AuthServiceImpl(userMapper, smsCodeMapper,
                orderMapper, dataExportRequestMapper,
                smsServiceProvider, emailServiceProvider,
                jwtUtil, redisTemplate, passwordEncoder, inviteService, eventPublisher, meterRegistry);
    }

    @Test
    @DisplayName("2FA 开启且未提交验证码：发送验证码并返回 TWO_FACTOR_REQUIRED，不签发 token")
    void login_twoFactorEnabled_withoutCode_shouldRequireChallengeAndNotIssueToken() {
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches(anyString(), eq("$hash"))).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("Password1!");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(428, ex.getCode());
        assertEquals("TWO_FACTOR_REQUIRED", ex.getMessage());
        // 必须发码，但绝不能签发 access token
        verify(emailService).sendTwoFactorCode(eq("user@example.com"), anyString(), anyInt());
        verify(jwtUtil, never()).generate(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("2FA 验证码错误：返回 TWO_FACTOR_INVALID，失败计数 INCR，不签发 token")
    void login_twoFactorEnabled_withWrongCode_shouldIncrementFailAndNotIssueToken() {
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), eq("$hash"))).thenReturn(true);
        when(valueOps.get("auth:2fa:1")).thenReturn("654321");
        when(valueOps.get("auth:2fa-fail:1")).thenReturn("0");
        when(valueOps.increment("auth:2fa-fail:1")).thenReturn(1L);

        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("Password1!");
        req.setTwoFactorCode("123456");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.login(req));
        assertEquals("TWO_FACTOR_INVALID", ex.getMessage());
        verify(valueOps).increment("auth:2fa-fail:1");
        verify(jwtUtil, never()).generate(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("开启 2FA：递增 token 版本、吊销 refresh token 并广播失效（踢掉全部旧会话）")
    void setTwoFactorEnabled_enable_shouldInvalidateExistingSessions() {
        user.setTwoFactorEnabled(false);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(valueOps.get("auth:2fa-verified:1")).thenReturn("1");

        authService.setTwoFactorEnabled(1L, true);

        verify(userMapper).updateById(user);
        verify(valueOps).increment("auth:token-version:1");
        verify(eventPublisher).publishEvent(any(UserTokenInvalidatedEvent.class));
    }

    @Test
    @DisplayName("邮箱验证码错误：返回统一文案并 INCR 失败计数")
    void confirmEmailVerification_wrongCode_shouldIncrementFail() {
        when(valueOps.get("auth:verify:user@example.com")).thenReturn("123456");
        when(valueOps.get("auth:verify-fail:user@example.com")).thenReturn("0");
        when(valueOps.increment("auth:verify-fail:user@example.com")).thenReturn(1L);

        EmailVerifyConfirmRequest req = new EmailVerifyConfirmRequest();
        req.setEmail("user@example.com");
        req.setCode("654321");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.confirmEmailVerification(req));
        assertEquals("Invalid or expired verification code", ex.getMessage());
        verify(valueOps).increment("auth:verify-fail:user@example.com");
    }
}

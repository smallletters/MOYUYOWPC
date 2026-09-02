package com.moyuyo.service;

import com.moyuyo.common.dto.auth.*;
import com.moyuyo.dao.entity.UserEntity;

public interface AuthService {

    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refreshToken(RefreshTokenRequest request);

    void logout(Long userId, String accessToken);

    void sendEmailVerification(EmailVerifyRequest request);

    void confirmEmailVerification(EmailVerifyConfirmRequest request);

    void sendPasswordReset(EmailVerifyRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    UserEntity getCurrentUser(Long userId);

    UserEntity updateCurrentUser(Long userId, ProfileUpdateRequest update);

    void sendMagicLink(EmailVerifyRequest request);

    TokenResponse verifyMagicLink(String token);

    void sendTwoFactorCode(Long userId);

    void verifyTwoFactorCode(Long userId, String code);

    /**
     * 设置两步验证开关。
     * 关闭时同时清掉 {@code auth:2fa-verified:userId} 缓存,
     * 确保下一次敏感操作(登录 / 大额支付)重新走二次校验。
     */
    UserEntity setTwoFactorEnabled(Long userId, boolean enabled);

    /**
     * 发送手机验证码。
     * @param phone 手机号（含国家区号）
     * @param purpose LOGIN / REGISTER / RESET_PASSWORD
     */
    void sendPhoneCode(String phone, String purpose);

    /**
     * 手机号 + 验证码登录。
     * 若手机号未注册则自动创建账号（生成随机密码、默认昵称），已注册则返回 JWT。
     */
    TokenResponse loginByPhone(String phone, String code);
}

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

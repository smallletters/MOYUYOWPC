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
}

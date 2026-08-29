package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.auth.*;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.AuthService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    @RateLimiter(name = "authLogin", fallbackMethod = "loginRateLimitFallback")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 登录限流降级方法：仅限流拒绝时触发，业务异常正常传播给全局异常处理器 */
    @SuppressWarnings("unused")
    private Result<TokenResponse> loginRateLimitFallback(LoginRequest request, RequestNotPermitted e) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        String token = UserContextHolder.getToken();
        authService.logout(UserContextHolder.getUserId(), token);
        return Result.success();
    }

    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/email/verify")
    @RateLimiter(name = "authLogin", fallbackMethod = "rateLimitFallback")
    public Result<Void> sendEmailVerification(@Valid @RequestBody EmailVerifyRequest request) {
        authService.sendEmailVerification(request);
        return Result.success();
    }

    @Operation(summary = "确认邮箱验证码")
    @PostMapping("/email/verify-confirm")
    public Result<Void> confirmEmailVerification(@Valid @RequestBody EmailVerifyConfirmRequest request) {
        authService.confirmEmailVerification(request);
        return Result.success();
    }

    @Operation(summary = "发送密码重置邮件")
    @PostMapping("/password/forgot")
    @RateLimiter(name = "authLogin", fallbackMethod = "rateLimitFallback")
    public Result<Void> forgotPassword(@Valid @RequestBody EmailVerifyRequest request) {
        authService.sendPasswordReset(request);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/password/reset")
    @RateLimiter(name = "authLogin", fallbackMethod = "resetPasswordRateLimitFallback")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }

    /** resetPassword 限流降级：签名与 resetPassword 一致，避免 Resilience4j 找不到 fallback 抛 NoSuchMethodError */
    @SuppressWarnings("unused")
    private Result<Void> resetPasswordRateLimitFallback(ResetPasswordRequest request, RequestNotPermitted e) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    @Operation(summary = "修改密码（需要登录）")
    @PostMapping("/password/change")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(UserContextHolder.getUserId(), request);
        return Result.success();
    }

    @Operation(summary = "发送 Magic Link 邮件")
    @PostMapping("/magic-link/send")
    @RateLimiter(name = "authLogin", fallbackMethod = "rateLimitFallback")
    public Result<Void> sendMagicLink(@Valid @RequestBody EmailVerifyRequest request) {
        authService.sendMagicLink(request);
        return Result.success();
    }

    @Operation(summary = "验证 Magic Link 并登录")
    @PostMapping("/magic-link/verify")
    public Result<TokenResponse> verifyMagicLink(@Valid @RequestBody MagicLinkVerifyRequest request) {
        return Result.success(authService.verifyMagicLink(request.getToken()));
    }

    @Operation(summary = "发送 2FA 验证码")
    @PostMapping("/2fa/send")
    public Result<Void> sendTwoFactorCode() {
        authService.sendTwoFactorCode(UserContextHolder.getUserId());
        return Result.success();
    }

    @Operation(summary = "验证 2FA 验证码")
    @PostMapping("/2fa/verify")
    public Result<Void> verifyTwoFactorCode(@Valid @RequestBody TwoFactorRequest request) {
        authService.verifyTwoFactorCode(UserContextHolder.getUserId(), request.getCode());
        return Result.success();
    }

    // ============ 手机短信验证码登录 ============

    @Operation(summary = "发送手机验证码")
    @PostMapping("/phone/send-code")
    @RateLimiter(name = "authLogin", fallbackMethod = "phoneSendCodeRateLimitFallback")
    public Result<Void> sendPhoneCode(@Valid @RequestBody PhoneSendCodeRequest request) {
        authService.sendPhoneCode(request.getPhone(), request.getPurpose() == null ? "LOGIN" : request.getPurpose());
        return Result.success();
    }

    /** phone/send-code 限流降级：签名与 sendPhoneCode 一致 */
    @SuppressWarnings("unused")
    private Result<Void> phoneSendCodeRateLimitFallback(PhoneSendCodeRequest request, RequestNotPermitted e) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    @Operation(summary = "手机号 + 验证码登录（未注册自动创建账号）")
    @PostMapping("/phone/login")
    @RateLimiter(name = "authLogin", fallbackMethod = "phoneLoginRateLimitFallback")
    public Result<TokenResponse> loginByPhone(@Valid @RequestBody PhoneLoginRequest request) {
        return Result.success(authService.loginByPhone(request.getPhone(), request.getCode()));
    }

    /** phone/login 限流降级：签名与 loginByPhone 一致 */
    @SuppressWarnings("unused")
    private Result<TokenResponse> phoneLoginRateLimitFallback(PhoneLoginRequest request, RequestNotPermitted e) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    /** 通用限流降级方法：仅限流拒绝时触发，业务异常正常传播给全局异常处理器 */
    @SuppressWarnings("unused")
    private Result<Void> rateLimitFallback(EmailVerifyRequest request, RequestNotPermitted e) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }
}

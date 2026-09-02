package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.auth.*;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.service.AuthService;

import java.util.HashMap;
import java.util.Map;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理")
@Slf4j
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
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        try {
            authService.sendTwoFactorCode(userId);
        } catch (IllegalArgumentException e) {
            // 邮箱未绑定 / 用户不存在 等用户侧错误,直接 400
            return Result.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            // 邮件服务未配置 / SMTP 不可用 等服务端问题 → 503
            log.warn("[2FA] send failed", e);
            return Result.serviceUnavailable(e.getMessage());
        }
        return Result.success();
    }

    @Operation(summary = "验证 2FA 验证码")
    @PostMapping("/2fa/verify")
    public Result<Void> verifyTwoFactorCode(@Valid @RequestBody TwoFactorRequest request) {
        authService.verifyTwoFactorCode(UserContextHolder.getUserId(), request.getCode());
        return Result.success();
    }

    /**
     * 设置两步验证开关。
     * <p>
     * 背景：账号与安全页面需要直接切换 2FA 状态,后端此前只暴露发送/验证接口,
     * 未提供"更新 mo_user.two_factor_enabled"端点,前端只能本地 mock。
     * <p>
     * 设计要点：
     * <ol>
     *   <li>需要登录态(JwtAuthFilter 已校验 token),无 userId 直接 401</li>
     *   <li>开启时强制二次身份校验——必须先调用 {@code POST /2fa/send} + {@code POST /2fa/verify},
     *       让 Redis 中存在 {@code auth:2fa-verified:userId} 才能开启;
     *       缺少校验返回 403 二阶段校验失败</li>
     *   <li>关闭不要求二次校验(用户主动关闭是预期行为);关闭后清理 verified 缓存</li>
     *   <li>返回精简 profile VO,前端只关心 twoFactorEnabled 字段,但保留 id/email/nickname
     *       便于前端 store 直接覆盖 userInfo 而不丢失其它字段</li>
     * </ol>
     */
    @Operation(summary = "开启/关闭两步验证")
    @PutMapping("/2fa")
    public Result<Map<String, Object>> toggleTwoFactor(@Valid @RequestBody TwoFactorToggleRequest request) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        UserEntity updated;
        try {
            updated = authService.setTwoFactorEnabled(userId, Boolean.TRUE.equals(request.getEnabled()));
        } catch (IllegalArgumentException e) {
            // 开启但未通过二阶段验证 → 403 让前端弹"请先输入验证码"
            String msg = e.getMessage();
            if (msg != null && msg.contains("二次验证")) {
                return Result.error(403, msg);
            }
            return Result.badRequest(msg);
        } catch (IllegalStateException e) {
            // Redis 不可用导致无法校验二次身份 → 503
            return Result.serviceUnavailable(e.getMessage());
        }
        Map<String, Object> p = new HashMap<>();
        p.put("id", updated.getId());
        p.put("email", updated.getEmail());
        p.put("nickname", updated.getNickname());
        p.put("avatar", updated.getAvatar());
        p.put("twoFactorEnabled",
                updated.getTwoFactorEnabled() != null && updated.getTwoFactorEnabled());
        return Result.success(p);
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

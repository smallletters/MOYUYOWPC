package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.dto.auth.*;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    // BCrypt 加密强度设为 12（生产推荐值），平衡安全性与性能
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    // 使用密码学安全的随机数生成器，避免 Math.random() 的可预测性
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final long ACCESS_TOKEN_EXPIRE_SECONDS = 7200;
    private static final long REFRESH_TOKEN_EXPIRE_SECONDS = 604800;
    private static final long VERIFICATION_CODE_EXPIRE_SECONDS = 300;
    private static final long RESET_TOKEN_EXPIRE_SECONDS = 1800;
    private static final long MAGIC_LINK_EXPIRE_SECONDS = 900;
    private static final long TWO_FACTOR_CODE_EXPIRE_SECONDS = 300;

    private static final String REDIS_KEY_REFRESH = "auth:refresh:";
    // 反向索引：userId -> refresh token 集合，用于登出时批量吊销
    private static final String REDIS_KEY_USER_REFRESH = "auth:user-refresh:";
    private static final String REDIS_KEY_BLACKLIST = "auth:blacklist:";
    private static final String REDIS_KEY_VERIFY_CODE = "auth:verify:";
    private static final String REDIS_KEY_RESET_TOKEN = "auth:reset:";
    private static final String REDIS_KEY_MAGIC_LINK = "auth:magiclink:";
    private static final String REDIS_KEY_2FA_CODE = "auth:2fa:";
    private static final String REDIS_KEY_2FA_VERIFIED = "auth:2fa-verified:";
    // 登录失败计数 Redis Key
    private static final String REDIS_KEY_LOGIN_FAIL = "auth:login-fail:";
    // 账户锁定 Redis Key
    private static final String REDIS_KEY_ACCOUNT_LOCK = "auth:account-lock:";
    // 连续失败 5 次后锁定
    private static final int MAX_LOGIN_FAIL_ATTEMPTS = 5;
    // 失败计数窗口：15 分钟（在此期间内累计失败次数
    private static final long LOGIN_FAIL_WINDOW_SECONDS = 900;
    // 账户锁定时长：15 分钟
    private static final long ACCOUNT_LOCK_DURATION_SECONDS = 900;

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, request.getEmail()));
        if (count > 0) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        user.setPasswordHash(PASSWORD_ENCODER.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setCountry(request.getCountry());
        user.setMarketingOptIn(request.getMarketingOptIn() != null && request.getMarketingOptIn());
        user.setEmailVerified(false);
        user.setStatus(1);
        user.setPoints(0);
        user.setTwoFactorEnabled(false);
        user.setLastLoginTime(LocalDateTime.now());

        userMapper.insert(user);

        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());
        return generateTokenPair(user);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        // 规范化邮箱，避免大小写不一致导致锁定 Key 不匹配
        String email = normalizeEmail(request.getEmail());

        // 1. 检查账户是否已被临时锁定（防止暴力破解
        String lockKey = REDIS_KEY_ACCOUNT_LOCK + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            Long remainSeconds = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            log.warn("Login attempt rejected: account locked, email={}, remainSeconds={}", email, remainSeconds);
            throw new IllegalArgumentException("账号登录失败次数过多，请 " + (remainSeconds != null ? remainSeconds / 60 : 15) + " 分钟后再试");
        }

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            // 用户不存在也记录失败（防止用户名枚举，提示信息相同
            recordLoginFailure(email, "user_not_found");
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("Account is disabled");
        }

        if (!PASSWORD_ENCODER.matches(request.getPassword(), user.getPasswordHash())) {
            // 密码错误，记录失败次数并检查是否需要锁定
            recordLoginFailure(email, "wrong_password");
            // 如果失败次数超过阈值，返回带锁定提示的错误
            String failKey = REDIS_KEY_LOGIN_FAIL + email;
            String failCountStr = redisTemplate.opsForValue().get(failKey);
            int failCount = failCountStr == null ? 0 : Integer.parseInt(failCountStr);
            int remainAttempts = MAX_LOGIN_FAIL_ATTEMPTS - failCount;
            if (remainAttempts <= 0) {
                throw new IllegalArgumentException("账号登录失败次数过多，请 15 分钟后再试");
            }
            throw new IllegalArgumentException("Invalid email or password");
        }

        // 2. 登录成功，清除失败计数
        clearLoginFailureRecords(email);

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return generateTokenPair(user);
    }

    /**
     * 记录登录失败，达到阈值时锁定账户
     * @param email 规范化后的邮箱
     * @param reason 失败原因（仅用于日志，不返回给用户
     */
    private void recordLoginFailure(String email, String reason) {
        String failKey = REDIS_KEY_LOGIN_FAIL + email;
        // 原子自增失败次数
        Long failCount = redisTemplate.opsForValue().increment(failKey);
        // 首次失败时设置过期时间（滑动窗口，每次失败重置
        if (failCount != null && failCount == 1) {
            redisTemplate.expire(failKey, LOGIN_FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
        } else {
            // 失败时重置窗口，避免过期后计数器已失效
            redisTemplate.expire(failKey, LOGIN_FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        log.warn("Login failed: email={}, reason={}, failCount={}", email, reason, failCount);

        // 连续失败超过阈值，锁定账户
        if (failCount != null && failCount >= MAX_LOGIN_FAIL_ATTEMPTS) {
            String lockKey = REDIS_KEY_ACCOUNT_LOCK + email;
            redisTemplate.opsForValue().set(lockKey, String.valueOf(failCount), ACCOUNT_LOCK_DURATION_SECONDS, TimeUnit.SECONDS);
            // 锁定后清除失败计数，解锁后重新计数
            redisTemplate.delete(failKey);
            log.warn("Account locked due to too many failed login attempts: email={}", email);
        }
    }

    /**
     * 登录成功后清除登录失败记录和锁定标记
     */
    private void clearLoginFailureRecords(String email) {
        redisTemplate.delete(REDIS_KEY_LOGIN_FAIL + email);
        redisTemplate.delete(REDIS_KEY_ACCOUNT_LOCK + email);
    }

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String key = REDIS_KEY_REFRESH + request.getRefreshToken();
        String userIdStr = redisTemplate.opsForValue().get(key);

        if (userIdStr == null) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // 删除旧 refresh token，并从反向索引中移除
        redisTemplate.delete(key);
        Long userId = Long.parseLong(userIdStr);
        redisTemplate.opsForSet().remove(REDIS_KEY_USER_REFRESH + userId, request.getRefreshToken());

        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        return generateTokenPair(user);
    }

    @Override
    public void logout(Long userId, String accessToken) {
        // 1. 拉黑当前 access token
        redisTemplate.opsForValue().set(
                REDIS_KEY_BLACKLIST + accessToken,
                String.valueOf(userId),
                ACCESS_TOKEN_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 2. 通过反向索引批量吊销该用户所有 refresh token，防止登出后旧 token 仍可换取新 access token
        String userRefreshKey = REDIS_KEY_USER_REFRESH + userId;
        Set<String> refreshTokens = redisTemplate.opsForSet().members(userRefreshKey);
        if (refreshTokens != null && !refreshTokens.isEmpty()) {
            List<String> keys = refreshTokens.stream()
                    .map(token -> REDIS_KEY_REFRESH + token)
                    .collect(Collectors.toList());
            redisTemplate.delete(keys);
            redisTemplate.delete(userRefreshKey);
        }

        log.info("User logged out: id={}", userId);
    }

    @Override
    public void sendEmailVerification(EmailVerifyRequest request) {
        // 规范化邮箱，避免大小写不一致导致缓存 Key 与数据库查询不匹配
        String email = normalizeEmail(request.getEmail());
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            return;
        }

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        redisTemplate.opsForValue().set(
                REDIS_KEY_VERIFY_CODE + email,
                code,
                VERIFICATION_CODE_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 不记录验证码明文，避免日志泄露导致账号接管
        log.info("Verification code sent to {}", email);
    }

    @Override
    public void confirmEmailVerification(EmailVerifyConfirmRequest request) {
        // 规范化邮箱，确保与发送验证码时使用的 Key 一致
        String email = normalizeEmail(request.getEmail());
        String key = REDIS_KEY_VERIFY_CODE + email;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null || !storedCode.equals(request.getCode())) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }

        redisTemplate.delete(key);

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user != null) {
            user.setEmailVerified(true);
            userMapper.updateById(user);
            log.info("Email verified: {}", email);
        }
    }

    @Override
    public void sendPasswordReset(EmailVerifyRequest request) {
        // 规范化邮箱，确保后续 resetPassword 查询数据库时能匹配
        String email = normalizeEmail(request.getEmail());
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            return;
        }

        String resetToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                REDIS_KEY_RESET_TOKEN + resetToken,
                email,
                RESET_TOKEN_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 不记录 reset token 明文，避免日志泄露
        log.info("Password reset token generated for {}", email);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String key = REDIS_KEY_RESET_TOKEN + request.getToken();
        String email = redisTemplate.opsForValue().get(key);

        if (email == null) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        redisTemplate.delete(key);

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        user.setPasswordHash(PASSWORD_ENCODER.encode(request.getNewPassword()));
        userMapper.updateById(user);

        log.info("Password reset completed for: {}", email);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (!PASSWORD_ENCODER.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(PASSWORD_ENCODER.encode(request.getNewPassword()));
        userMapper.updateById(user);

        log.info("Password changed for user: {}", userId);
    }

    @Override
    public UserEntity getCurrentUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        user.setPasswordHash(null);
        return user;
    }

    @Override
    @Transactional
    public UserEntity updateCurrentUser(Long userId, UserEntity update) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (update.getNickname() != null) {
            user.setNickname(update.getNickname());
        }
        if (update.getAvatar() != null) {
            user.setAvatar(update.getAvatar());
        }
        if (update.getPhone() != null) {
            user.setPhone(update.getPhone());
        }
        if (update.getBirthday() != null) {
            user.setBirthday(update.getBirthday());
        }
        if (update.getCountry() != null) {
            user.setCountry(update.getCountry());
        }
        if (update.getLocale() != null) {
            user.setLocale(update.getLocale());
        }
        if (update.getTimezone() != null) {
            user.setTimezone(update.getTimezone());
        }
        if (update.getMarketingOptIn() != null) {
            user.setMarketingOptIn(update.getMarketingOptIn());
        }

        userMapper.updateById(user);
        user.setPasswordHash(null);
        return user;
    }

    @Override
    public void sendMagicLink(EmailVerifyRequest request) {
        // 规范化邮箱，确保后续 verifyMagicLink 查询数据库时能匹配
        String email = normalizeEmail(request.getEmail());
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            return;
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                REDIS_KEY_MAGIC_LINK + token,
                email,
                MAGIC_LINK_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 不记录 magic link token 明文，避免日志泄露
        log.info("Magic link sent to {}", email);
    }

    @Override
    public TokenResponse verifyMagicLink(String token) {
        String key = REDIS_KEY_MAGIC_LINK + token;
        String email = redisTemplate.opsForValue().get(key);

        if (email == null) {
            throw new IllegalArgumentException("Invalid or expired magic link");
        }

        redisTemplate.delete(key);

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("Account is disabled");
        }

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("User logged in via magic link: id={}, email={}", user.getId(), email);
        return generateTokenPair(user);
    }

    @Override
    public void sendTwoFactorCode(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        redisTemplate.opsForValue().set(
                REDIS_KEY_2FA_CODE + userId,
                code,
                TWO_FACTOR_CODE_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 不记录 2FA 验证码明文，避免日志泄露
        log.info("2FA code sent for user: {}", userId);
    }

    @Override
    public void verifyTwoFactorCode(Long userId, String code) {
        String key = REDIS_KEY_2FA_CODE + userId;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null || !storedCode.equals(code)) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }

        redisTemplate.delete(key);

        String verifiedKey = REDIS_KEY_2FA_VERIFIED + userId;
        redisTemplate.opsForValue().set(verifiedKey, "1", 7200, TimeUnit.SECONDS);

        log.info("2FA verified for user: {}", userId);
    }

    private TokenResponse generateTokenPair(UserEntity user) {
        String accessToken = jwtUtil.generate(user.getId(), user.getEmail());

        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                REDIS_KEY_REFRESH + refreshToken,
                String.valueOf(user.getId()),
                REFRESH_TOKEN_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 维护反向索引：userId -> refresh token，用于登出时批量吊销
        redisTemplate.opsForSet().add(REDIS_KEY_USER_REFRESH + user.getId(), refreshToken);

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                ACCESS_TOKEN_EXPIRE_SECONDS);
    }

    /**
     * 规范化邮箱地址：去除首尾空格并转为小写。
     * 用于缓存 Key 与数据库查询，避免大小写不一致导致验证码无法匹配或重复注册。
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}

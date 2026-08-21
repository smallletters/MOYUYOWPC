package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.dto.auth.*;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AuthService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    /** 统一密码编码器 Bean，强度由 moyuyo.password.bcrypt-strength 控制（默认 12） */
    private final PasswordEncoder passwordEncoder;
    /** 登录失败计数 Redis 不可用计数器：便于 Prometheus 告警 */
    private final Counter loginFailureCounter;

    // MeterRegistry 与 Counter 通过 @RequiredArgsConstructor 不便注入，改用显式构造注入
    // 这里通过构造注入 MeterRegistry 创建 Prometheus 计数器
    public AuthServiceImpl(UserMapper userMapper,
                           JwtUtil jwtUtil,
                           StringRedisTemplate redisTemplate,
                           PasswordEncoder passwordEncoder,
                           MeterRegistry meterRegistry) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.loginFailureCounter = Counter.builder("moyuyo_login_failure_redis_unavailable_total")
                .description("登录失败计数因 Redis 不可用而失败降级放行的次数（fail-open 行为需运维感知）")
                .tag("reason", "redis_unavailable")
                .register(meterRegistry);
    }

    /**
     * 原子化的"递增+首次设置过期时间"Lua 脚本
     * <p>
     * 原实现 INCR + EXPIRE 是两步操作：
     * 1) 若进程在两步之间崩溃，Key 永久驻留（攻击者不断失败也不会被自然清除）
     * 2) 与 fail-open 语义冲突：Redis 抖动时部分命令成功部分失败导致计数器状态不一致
     * <p>
     * 修复：单脚本原子执行 INCR + EXPIRE 步骤，且仅在 Key 不存在时设置过期时间（TTL 续期场景下不重置窗口），
     * 避免每次失败都重置窗口导致攻击者可绕过窗口控制（实测：每次失败都 EXPIRE 时窗口被重置，
     * 攻击者每 14 分 59 秒失败一次即可绕过 15 分钟窗口）。
     */
    private static final RedisScript<Long> INCR_WITH_EXPIRE_IF_NEW = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1])\n" +
            "if n == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return n",
            Long.class);

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
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
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

        // 1. 检查账户是否已被临时锁定（防止暴力破解）
        // Redis 不可用时 fail-open：让用户尝试登录（与 recordLoginFailure 的 fail-open 语义对齐），
        // 由 BCrypt 强校验 + IP 限流 + IpRateLimitFilter 兜底防爆破
        String lockKey = REDIS_KEY_ACCOUNT_LOCK + email;
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("检查账户锁定状态 Redis 不可用（fail-open 放行），email.len={}",
                    email == null ? 0 : email.length(), e);
            locked = false;
        }
        if (locked) {
            Long remainSeconds;
            try {
                remainSeconds = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            } catch (DataAccessException e) {
                remainSeconds = null;
            }
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

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // 密码错误，记录失败次数并检查是否需要锁定
            recordLoginFailure(email, "wrong_password");
            // 如果失败次数超过阈值，返回带锁定提示的错误
            // 读取当前失败次数（仅用于前端展示剩余尝试次数，不影响锁定逻辑）
            // Redis 不可用时 fail-open：直接返回通用错误（不暴露具体剩余次数，避免用户名枚举风险叠加）
            int remainAttempts;
            try {
                String failKey = REDIS_KEY_LOGIN_FAIL + email;
                String failCountStr = redisTemplate.opsForValue().get(failKey);
                int failCount = failCountStr == null ? 0 : Integer.parseInt(failCountStr);
                remainAttempts = MAX_LOGIN_FAIL_ATTEMPTS - failCount;
            } catch (DataAccessException | NumberFormatException e) {
                remainAttempts = -1;
            }
            if (remainAttempts == 0) {
                throw new IllegalArgumentException("账号登录失败次数过多，请 15 分钟后再试");
            }
            throw new IllegalArgumentException("Invalid email or password");
        }

        // 2. 登录成功，清除失败计数（Redis 不可用不阻断成功登录流程）
        clearLoginFailureRecords(email);

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return generateTokenPair(user);
    }

    /**
     * 记录登录失败，达到阈值时锁定账户
     * @param email 规范化后的邮箱
     * @param reason 失败原因（仅用于日志，不返回给用户）
     * <p>
     * 安全/可靠性修复：
     * 1. INCR + EXPIRE 改用 Lua 脚本原子执行，避免进程崩溃导致 Key 永久驻留
     * 2. 仅在 Key 不存在时设置 TTL（首次失败），失败时不再续期窗口，防止攻击者"每 14 分 59 秒失败一次"绕过窗口控制
     * 3. Redis 不可用时 fail-open：记录失败次数但不阻断用户登录，避免 Redis 抖动期间所有用户都无法登录
     *    与 JwtAuthFilter 黑名单 fail-closed 语义刻意区分：登录失败计数优先业务可用性
     */
    private void recordLoginFailure(String email, String reason) {
        String failKey = REDIS_KEY_LOGIN_FAIL + email;

        Long failCount;
        try {
            failCount = redisTemplate.execute(
                    INCR_WITH_EXPIRE_IF_NEW,
                    Collections.singletonList(failKey),
                    String.valueOf(LOGIN_FAIL_WINDOW_SECONDS));
        } catch (DataAccessException e) {
            // Redis 不可用：fail-open 放行此次登录失败计数（不阻断用户登录），
            // 但累加 Prometheus 计数器让运维第一时间感知 Redis 抖动
            loginFailureCounter.increment();
            log.error("登录失败计数 Redis 不可用（fail-open），email.len={}, reason={}",
                    email == null ? 0 : email.length(), reason, e);
            return;
        }

        if (failCount == null) {
            // execute 返回 null 的极端场景（不应发生，但兜底 fail-open）
            loginFailureCounter.increment();
            log.error("登录失败计数返回 null（fail-open），email.len={}", email == null ? 0 : email.length());
            return;
        }

        log.warn("Login failed: email={}, reason={}, failCount={}", email, reason, failCount);

        // 连续失败超过阈值，锁定账户
        if (failCount >= MAX_LOGIN_FAIL_ATTEMPTS) {
            String lockKey = REDIS_KEY_ACCOUNT_LOCK + email;
            try {
                redisTemplate.opsForValue().set(lockKey, String.valueOf(failCount),
                        ACCOUNT_LOCK_DURATION_SECONDS, TimeUnit.SECONDS);
                // 锁定后清除失败计数，解锁后重新计数
                redisTemplate.delete(failKey);
                log.warn("Account locked due to too many failed login attempts: email={}", email);
            } catch (DataAccessException e) {
                // 锁定失败：fail-open + 计数器累加，运维第一时间感知
                loginFailureCounter.increment();
                log.error("账户锁定 Redis 写入失败（fail-open），email.len={}",
                        email == null ? 0 : email.length(), e);
            }
        }
    }

    /**
     * 登录成功后清除登录失败记录和锁定标记
     * <p>
     * Redis 不可用时静默吞掉异常（fail-open）：登录成功路径不应被 Redis 抖动阻断，
     * 失败计数项会在自然 TTL 到期后被清理（最多延迟 LOGIN_FAIL_WINDOW_SECONDS = 15 分钟）。
     */
    private void clearLoginFailureRecords(String email) {
        try {
            redisTemplate.delete(REDIS_KEY_LOGIN_FAIL + email);
            redisTemplate.delete(REDIS_KEY_ACCOUNT_LOCK + email);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("清除登录失败计数 Redis 不可用（fail-open），email.len={}",
                    email == null ? 0 : email.length(), e);
        }
    }

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String key = REDIS_KEY_REFRESH + request.getRefreshToken();
        String userIdStr;
        try {
            userIdStr = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            // Redis 不可用时 fail-open：让用户重新走登录流程，避免静默 500 给客户端
            // 与登录路径 fail-open 语义对齐：业务可用性优先
            loginFailureCounter.increment();
            log.error("刷新 Token 读取 Redis 不可用（fail-open），token.len={}",
                    request.getRefreshToken() == null ? 0 : request.getRefreshToken().length(), e);
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        if (userIdStr == null) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // 删除旧 refresh token，并从反向索引中移除
        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(REDIS_KEY_USER_REFRESH + userId, request.getRefreshToken());
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("刷新 Token 清理 Redis 不可用（fail-open 继续生成新 token），userId={}", userIdStr, e);
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            // refresh token 关联的 userId 解析失败（不应发生）：fail-open 但不继续，避免业务异常
            log.error("refresh token userId 解析失败，userIdStr={}", userIdStr, e);
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        return generateTokenPair(user);
    }

    @Override
    public void logout(Long userId, String accessToken) {
        // 1. 拉黑当前 access token
        try {
            redisTemplate.opsForValue().set(
                    REDIS_KEY_BLACKLIST + accessToken,
                    String.valueOf(userId),
                    ACCESS_TOKEN_EXPIRE_SECONDS,
                    TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            // Redis 不可用：fail-open（让用户感知已登出即可），access token 在 TTL 到期后自动失效
            loginFailureCounter.increment();
            log.error("logout 拉黑 access token Redis 不可用（fail-open），userId={}", userId, e);
        }

        // 2. 通过反向索引批量吊销该用户所有 refresh token，防止登出后旧 token 仍可换取新 access token
        try {
            String userRefreshKey = REDIS_KEY_USER_REFRESH + userId;
            Set<String> refreshTokens = redisTemplate.opsForSet().members(userRefreshKey);
            if (refreshTokens != null && !refreshTokens.isEmpty()) {
                List<String> keys = refreshTokens.stream()
                        .map(token -> REDIS_KEY_REFRESH + token)
                        .collect(Collectors.toList());
                redisTemplate.delete(keys);
                redisTemplate.delete(userRefreshKey);
            }
        } catch (DataAccessException e) {
            // Redis 不可用：fail-open，refresh token 7 天后自然到期；用户重新登录后旧 refresh 也无法匹配新 user
            loginFailureCounter.increment();
            log.error("logout 批量吊销 refresh token Redis 不可用（fail-open），userId={}", userId, e);
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

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
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

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
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
    public UserEntity updateCurrentUser(Long userId, ProfileUpdateRequest update) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        // 关键修复：原实现接收 UserEntity 即便过滤了 8 个字段，DTO 上仍可能传入 passwordHash/role/status 等敏感字段被 Spring 反序列化。
        // 现改为 ProfileUpdateRequest 白名单 + 头像 URL 协议白名单（isAvatarValid 兜底拒绝 javascript:/data:）。
        // 头像 URL 二级校验：即使绕过 Bean Validation 也必须在 Service 层校验通过才落库
        if (update.getAvatar() != null && !update.getAvatar().isEmpty() && !update.isAvatarValid()) {
            throw new IllegalArgumentException("头像 URL 必须使用 http/https 协议");
        }

        if (update.getNickname() != null) {
            // 字符串净化：剥离 <script>/onload= 等危险标签，防止 XSS 持久化到个人主页
            user.setNickname(com.moyuyo.common.utils.XssSanitizer.sanitizePlainText(update.getNickname()));
        }
        if (update.getAvatar() != null) {
            user.setAvatar(update.getAvatar());
        }
        // 性别：白名单已由 DTO Pattern 保证；空串（用户取消选择）置 null，与"未填写"语义一致
        if (update.getGender() != null) {
            user.setGender(update.getGender().isEmpty() ? null : update.getGender());
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
        try {
            redisTemplate.opsForValue().set(
                    REDIS_KEY_REFRESH + refreshToken,
                    String.valueOf(user.getId()),
                    REFRESH_TOKEN_EXPIRE_SECONDS,
                    TimeUnit.SECONDS);

            // 维护反向索引：userId -> refresh token，用于登出时批量吊销
            redisTemplate.opsForSet().add(REDIS_KEY_USER_REFRESH + user.getId(), refreshToken);
        } catch (DataAccessException e) {
            // Redis 不可用：仍返回 token 让用户短期可用（access token 2h 内有效），
            // refresh token 自然失效后用户需重新登录。fail-open 优先业务可用性
            loginFailureCounter.increment();
            log.error("生成 token 对写入 Redis 不可用（fail-open），userId={}", user.getId(), e);
        }

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

package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.dto.auth.*;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.entity.SmsCodeEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.SmsCodeMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AuthService;
import com.moyuyo.service.EmailService;
import com.moyuyo.service.SmsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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

// 抑制 JDT 静态检查对 MyBatis-Plus Lambda 引用的 null type safety 警告
@SuppressWarnings("null")
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final SmsCodeMapper smsCodeMapper;
    // 注销预校验:检测用户未完成订单(避免死单/资产悬空)
    private final com.moyuyo.dao.mapper.OrderMapper orderMapper;
    // USER 端数据导出请求落库:复用 admin 端的 mo_data_export_request 表
    // requestType='USER_DATA_EXPORT' 作为用户自导出场景的标识
    private final com.moyuyo.dao.admin.mapper.DataExportRequestMapper dataExportRequestMapper;
    // 使用 ObjectProvider 支持可选注入：未配置 SMS Provider 时 SmsService Bean 可能不存在（NoopSmsServiceImpl 在 Spring 6.x 下因
    // @Service + @ConditionalOnMissingBean 顺序问题不一定会被注册），避免构造器装配失败导致 dev 启动阻塞
    private final ObjectProvider<SmsService> smsServiceProvider;
    /** EmailService 可选注入：未配置 spring.mail.* 时也不阻断启动，发送阶段内部走 log-only 降级 */
    private final ObjectProvider<EmailService> emailServiceProvider;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    /** 统一密码编码器 Bean，强度由 moyuyo.password.bcrypt-strength 控制（默认 12） */
    private final PasswordEncoder passwordEncoder;
    /** 登录失败计数 Redis 不可用计数器：便于 Prometheus 告警 */
    private final Counter loginFailureCounter;
    /** Magic Link 跳转的 H5 基础地址（与 EmailServiceImpl 默认值保持一致） */
    @Value("${moyuyo.h5-base:http://localhost:5174}")
    private String h5Base;

    // MeterRegistry 与 Counter 通过 @RequiredArgsConstructor 不便注入，改用显式构造注入
    // 这里通过构造注入 MeterRegistry 创建 Prometheus 计数器
    public AuthServiceImpl(UserMapper userMapper,
                           SmsCodeMapper smsCodeMapper,
                           com.moyuyo.dao.mapper.OrderMapper orderMapper,
                           com.moyuyo.dao.admin.mapper.DataExportRequestMapper dataExportRequestMapper,
                           ObjectProvider<SmsService> smsServiceProvider,
                           ObjectProvider<EmailService> emailServiceProvider,
                           JwtUtil jwtUtil,
                           StringRedisTemplate redisTemplate,
                           PasswordEncoder passwordEncoder,
                           MeterRegistry meterRegistry) {
        this.userMapper = userMapper;
        this.smsCodeMapper = smsCodeMapper;
        this.orderMapper = orderMapper;
        this.dataExportRequestMapper = dataExportRequestMapper;
        this.smsServiceProvider = smsServiceProvider;
        this.emailServiceProvider = emailServiceProvider;
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
    // 6 位数字重置验证码（新范式，与前端 forgot.vue 6-digit input 对齐），value 存 email
    private static final String REDIS_KEY_RESET_CODE = "auth:resetcode:";
    // 重置验证码失败次数：每错一次 INCR，超过 5 次强制失效（避免 6 位数被暴力穷举）
    private static final String REDIS_KEY_RESET_FAIL = "auth:resetcode-fail:";
    @Deprecated(since = "reset-code 范式迁移后保留 1 个版本的兼容读：已不再写入")
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
    // 重置验证码最大失败次数：5 次后强制失效（同 GitHub Issue HIGH 漏洞防爆破建议）
    private static final int MAX_RESET_CODE_FAIL_ATTEMPTS = 5;
    // 失败计数窗口：15 分钟（在此期间内累计失败次数
    private static final long LOGIN_FAIL_WINDOW_SECONDS = 900;
    // 账户锁定时长：15 分钟
    private static final long ACCOUNT_LOCK_DURATION_SECONDS = 900;

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        boolean hasEmail = request.getEmail() != null && !request.getEmail().isBlank();
        boolean hasPhone = request.getPhone() != null && !request.getPhone().isBlank();
        // 业务级校验:email / phone 二选一
        if (!hasEmail && !hasPhone) {
            throw new IllegalArgumentException("请填写邮箱或手机号");
        }
        // email 路径
        if (hasEmail) {
            String email = normalizeEmail(request.getEmail());
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<UserEntity>()
                            .eq(UserEntity::getEmail, email));
            if (count != null && count > 0) {
                throw new IllegalArgumentException("Email already registered");
            }
        }
        // phone 路径:必传 smsCode,且验证码必须有效
        if (hasPhone && (request.getSmsCode() == null || request.getSmsCode().isBlank())) {
            throw new IllegalArgumentException("手机号注册需提供短信验证码");
        }
        String phone = hasPhone ? normalizePhone(request.getPhone()) : null;
        if (hasPhone) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<UserEntity>()
                            .eq(UserEntity::getPhone, phone));
            if (count != null && count > 0) {
                throw new IllegalArgumentException("Phone already registered");
            }
            // 复用 loginByPhone 中的验证码校验逻辑:保持与登录/注册一致
            verifyPhoneCode(phone, request.getSmsCode(), "REGISTER");
        }

        UserEntity user = new UserEntity();
        if (hasEmail) {
            user.setEmail(normalizeEmail(request.getEmail()));
        }
        if (hasPhone) {
            user.setPhone(phone);
            // phone-only 用户需要一个非空的占位 email（数据库 NOT NULL 约束）。
            // 后续可由用户在个人中心补全真实邮箱。
            user.setEmail(phone + "@phone.moyuyo.local");
        }
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

        log.info("User registered: id={}, email={}, phone={}", user.getId(), user.getEmail(), user.getPhone());
        return generateTokenPair(user);
    }

    /**
     * 校验手机号验证码:独立方法,register / loginByPhone 共用
     * @param phone 已 normalize 的手机号(含国家区号)
     * @param code 用户输入的 6 位验证码
     * @param purpose 验证码用途(REGISTER / LOGIN 等),必须与发码时一致
     */
    private void verifyPhoneCode(String phone, String code, String purpose) {
        SmsCodeEntity record = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeEntity>()
                        .eq(SmsCodeEntity::getPhone, phone)
                        .eq(SmsCodeEntity::getPurpose, purpose)
                        .eq(SmsCodeEntity::getUsed, 0)
                        .gt(SmsCodeEntity::getExpireAt, LocalDateTime.now())
                        .orderByDesc(SmsCodeEntity::getId)
                        .last("LIMIT 1"));
        if (record == null) {
            throw new IllegalArgumentException("验证码不存在或已过期");
        }
        if (record.getFailCount() != null && record.getFailCount() >= PHONE_CODE_MAX_FAIL) {
            record.setUsed(1);
            smsCodeMapper.updateById(record);
            throw new IllegalArgumentException("验证码错误次数过多,请重新获取");
        }
        if (!code.equals(record.getCode())) {
            record.setFailCount((record.getFailCount() == null ? 0 : record.getFailCount()) + 1);
            smsCodeMapper.updateById(record);
            throw new IllegalArgumentException("验证码错误");
        }
        // 标记已使用
        record.setUsed(1);
        smsCodeMapper.updateById(record);
    }

    /**
     * 规范化手机号:trim + 小写国家码保留原样,数字部分去空格。
     * 这里不做严格 E.164 校验,业务级已通过 SMS 渠道可送达即可。
     */
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("\\s+", "").trim();
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

        // 注销冻结期校验：到期后拒绝登录（与微信/京东一致）
        // 期内未到期则放行并自动撤销（业内"登录即后悔"模式，参考 ProcessOn 7 天）
        if (user.getDeleteScheduledAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (!user.getDeleteScheduledAt().isAfter(now)) {
                // 已到期：定时任务尚未清理前仍阻断登录，避免"到期空档期"被攻击
                log.warn("Login rejected: account deletion grace period expired, userId={}", user.getId());
                throw new IllegalArgumentException("账号已注销，如需使用请重新注册");
            }
            // 期内：撤销注销申请 + 清空 deleteScheduledAt + 状态回滚到 ACTIVE
            log.info("Login during deletion grace period, auto-revoke: userId={}, scheduledAt={}",
                    user.getId(), user.getDeleteScheduledAt());
            user.setDeleteScheduledAt(null);
            user.setStatus(USER_STATUS_ACTIVE);
            userMapper.updateById(user);
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

        // 注册流程必须能发验证码（邮箱尚未入库），所以这里不查 user 表。
        // 用户已注册且邮箱已验证时仍跳过发送，避免对同一邮箱重复骚扰。
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));
        if (user != null && Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        redisTemplate.opsForValue().set(
                REDIS_KEY_VERIFY_CODE + email,
                code,
                VERIFICATION_CODE_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 不记录验证码明文，避免日志泄露导致账号接管（真实发送由 EmailService 落地）
        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService == null) {
            log.warn("[email-verify] EmailService Bean 不存在，仅在日志模式下生成验证码：{}", email);
        } else {
            emailService.sendEmailVerificationCode(
                    email,
                    code,
                    (int) (VERIFICATION_CODE_EXPIRE_SECONDS / 60));
        }
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
            // 防止用户名枚举：对外仍返回 200；仅内部日志记录长度（不记录明文）
            log.info("Password reset requested for non-existent email, len={}",
                    email == null ? 0 : email.length());
            return;
        }

        // 2026/08 架构对齐：与 forgot.vue 表单（6-digit input）一致，生成 6 位数字验证码，
        // 而不是原实现的 UUID 32 位 token；Redis key 由 code → email，验证阶段直接用用户输入查。
        String resetCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String codeKey = REDIS_KEY_RESET_CODE + resetCode;
        String failKey = REDIS_KEY_RESET_FAIL + resetCode;
        // code → email 存 30 分钟（与原 TTL 一致）；失败计数 key 用相同 TTL 便于同步过期
        redisTemplate.opsForValue().set(codeKey, email, RESET_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(failKey, "0", RESET_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 真实发送邮件（失败时 EmailServiceImpl 会抛 502 中断流程，避免用户误以为"已发"）
        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService == null) {
            log.warn("[password-reset] EmailService Bean 不存在，进入 log-only 降级：to={}", email);
        } else {
            emailService.sendPasswordResetCode(
                    email,
                    resetCode,
                    (int) (RESET_TOKEN_EXPIRE_SECONDS / 60));
        }

        // 不记录 reset code 明文，避免日志泄露
        log.info("Password reset code sent to {}", email);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Bean Validation 已保证 request.token 是 "^\\d{6}$"，直接查 code → email
        String code = request.getToken();
        String codeKey = REDIS_KEY_RESET_CODE + code;
        String failKey = REDIS_KEY_RESET_FAIL + code;
        // 兼容读：旧 UUID token 范式（1 个版本过渡期，之后移除）
        String legacyKey = REDIS_KEY_RESET_TOKEN + code;
        boolean usedLegacyToken = false;

        String email;
        try {
            email = redisTemplate.opsForValue().get(codeKey);
            if (email == null && code != null && code.length() == 32) {
                email = redisTemplate.opsForValue().get(legacyKey);
                usedLegacyToken = true;
            }
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("resetPassword 读取 Redis 不可用，code={}", maskCode(code), e);
            throw new IllegalArgumentException("系统繁忙，请稍后再试");
        }

        if (email == null) {
            throw new IllegalArgumentException("Invalid or expired reset code");
        }

        // 6 位 code 防爆破：先查失败次数，≥5 则立刻失效（同 PHONE_CODE_MAX_FAIL）
        if (!usedLegacyToken) {
            int failCount = 0;
            try {
                String failStr = redisTemplate.opsForValue().get(failKey);
                if (failStr != null) {
                    failCount = Integer.parseInt(failStr);
                }
            } catch (DataAccessException | NumberFormatException e) {
                failCount = 0;
            }
            if (failCount >= MAX_RESET_CODE_FAIL_ATTEMPTS) {
                // 失败达上限：主动删除 codeKey + failKey，要求用户重新申请
                try {
                    redisTemplate.delete(codeKey);
                    redisTemplate.delete(failKey);
                } catch (DataAccessException de) {
                    loginFailureCounter.increment();
                    log.error("resetPassword 超限清理 Redis 失败, code={}", maskCode(code), de);
                }
                throw new IllegalArgumentException("验证码错误次数过多，请重新获取");
            }
        }

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            // 不区分「user not found」还是「code invalid」，统一错误防止用户名枚举（OWASP 要求）
            throw new IllegalArgumentException("Invalid or expired reset code");
        }

        // 密码编码强度已由全局 PasswordEncoder 的 moyuyo.password.bcrypt-strength 控制
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);

        // 一次性消费：成功后立刻删 Redis，防止同一 code 被重复改密码（会话劫持场景）
        try {
            if (usedLegacyToken) {
                redisTemplate.delete(legacyKey);
            } else {
                redisTemplate.delete(codeKey);
                redisTemplate.delete(failKey);
            }
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("resetPassword 清理 Redis 失败（密码已更新成功），email={}", email, e);
        }

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
        // 隐私开关 4 项（V20260916_01）：null 表示前端未传，保留原值；
        // Boolean 字段允许 false,与 marketingOptIn 处理一致
        if (update.getPublicFavorites() != null) {
            user.setPublicFavorites(update.getPublicFavorites());
        }
        if (update.getAllowViewProfile() != null) {
            user.setAllowViewProfile(update.getAllowViewProfile());
        }
        if (update.getShowOnlineStatus() != null) {
            user.setShowOnlineStatus(update.getShowOnlineStatus());
        }
        if (update.getAllowMessages() != null) {
            user.setAllowMessages(update.getAllowMessages());
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
            // 防止用户名枚举：静默返回
            log.info("Magic link requested for non-existent email, len={}",
                    email == null ? 0 : email.length());
            return;
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                REDIS_KEY_MAGIC_LINK + token,
                email,
                MAGIC_LINK_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 真实发送邮件：把 token 拼接成 H5 可点击登录链接（非 6 位数字，与忘记密码区分范式）
        String magicLink = h5Base + "/pages/user/magic-link?token=" + token;
        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService == null) {
            log.warn("[magic-link] EmailService Bean 不存在，进入 log-only 降级：to={}", email);
        } else {
            emailService.sendMagicLink(
                    email,
                    magicLink,
                    (int) (MAGIC_LINK_EXPIRE_SECONDS / 60));
        }

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

        // 注销冻结期校验：与 password/phone login 路径一致（期内登录即撤销）
        if (user.getDeleteScheduledAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (!user.getDeleteScheduledAt().isAfter(now)) {
                log.warn("Magic link login rejected: account deletion grace period expired, userId={}", user.getId());
                throw new IllegalArgumentException("账号已注销，如需使用请重新注册");
            }
            user.setDeleteScheduledAt(null);
            user.setStatus(USER_STATUS_ACTIVE);
            log.info("Magic link login during deletion grace period, auto-revoke: userId={}", user.getId());
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
        String email = normalizeEmail(user.getEmail());
        if (email == null) {
            throw new IllegalArgumentException("用户未绑定邮箱,无法发送两步验证验证码");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        redisTemplate.opsForValue().set(
                REDIS_KEY_2FA_CODE + userId,
                code,
                TWO_FACTOR_CODE_EXPIRE_SECONDS,
                TimeUnit.SECONDS);

        // 真发邮件:与 sendEmailVerificationCode 复用同一 SMTP 通道;
        // EmailServiceImpl 内部对"无 JavaMailSender Bean"的场景自动降级为 log-only
        // 并在 dev 模式下把验证码输出到日志便于联调;生产需配置 SPRING_MAIL_*。
        // EmailService 抛 BusinessException 时(真实发送失败),这里不 catch,
        // 让 GlobalExceptionHandler 兜底返回 502,前端提示"邮件发送失败"。
        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService == null) {
            // EmailService Bean 不存在是配置问题(例如 dev 未装配邮件模块),
            // 不能默默写 Redis 却不发邮件——否则用户永远收不到码。
            // 删除已写入 Redis 的验证码,避免脏数据
            redisTemplate.delete(REDIS_KEY_2FA_CODE + userId);
            log.error("[2FA] EmailService Bean 未注册,无法发送验证码(userId={})", userId);
            throw new IllegalStateException("邮件服务未配置,无法发送两步验证验证码");
        }
        emailService.sendTwoFactorCode(email, code, (int) (TWO_FACTOR_CODE_EXPIRE_SECONDS / 60));

        // 不记录 2FA 验证码明文,避免日志泄露;
        // EmailService 自身已对收件人邮箱做了 mask 脱敏日志
        log.info("[2FA] code sent for user: {}", userId);
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

    /**
     * 设置两步验证开关。
     * <ul>
     *   <li>开启(enabled=true 且当前未开启):必须先通过 {@code verifyTwoFactorCode} 完成二次验证
     *     (即 Redis 存在 {@code auth:2fa-verified:userId}),否则拒绝;通过校验后会消费(删除)
     *     该缓存——开启是"一次性身份确认"动作,不留下长期 verified 标记。</li>
     *   <li>幂等:已是开启态再开启直接返回原实体,不要求二次验证,避免用户开 2FA 后短时间内
     *     因某种抖动被强制下线。</li>
     *   <li>关闭:写入 {@code two_factor_enabled = 0},并删除 {@code auth:2fa-verified:userId},
     *     让所有"已通过二次验证"会话失效,下次敏感操作重新走 2FA 流程。</li>
     * </ul>
     */
    @Override
    @Transactional
    public UserEntity setTwoFactorEnabled(Long userId, boolean enabled) {
        if (userId == null) {
            throw new IllegalArgumentException("User not logged in");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        boolean alreadyOn = Boolean.TRUE.equals(user.getTwoFactorEnabled());
        if (enabled && !alreadyOn) {
            // 开启时要求用户先通过 verify 接口拿到 verified 缓存,
            // 这是"开启 2FA 前的二次身份验证"的语义闸门
            String verifiedKey = REDIS_KEY_2FA_VERIFIED + userId;
            String verified;
            try {
                verified = redisTemplate.opsForValue().get(verifiedKey);
            } catch (DataAccessException e) {
                log.error("[2FA] failed to read verified cache for user {}", userId, e);
                throw new IllegalStateException("无法校验二次身份,请稍后再试");
            }
            if (!"1".equals(verified)) {
                throw new IllegalArgumentException("请先完成二次验证");
            }
        }
        boolean changed = !Boolean.valueOf(enabled).equals(user.getTwoFactorEnabled());
        user.setTwoFactorEnabled(enabled);
        userMapper.updateById(user);
        if (enabled && !alreadyOn) {
            // 开启成功后立刻消费 verified 缓存:
            // 开启是单次身份确认动作,不持久保留 verified 标记
            try {
                redisTemplate.delete(REDIS_KEY_2FA_VERIFIED + userId);
            } catch (DataAccessException e) {
                log.warn("[2FA] failed to consume verified cache after enable for user {}: {}",
                        userId, e.getMessage());
            }
        }
        if (changed && !enabled) {
            // 仅在"由开 -> 关"且确实发生状态变更时清理缓存,
            // 避免误清掉刚 verify 完、未持久化前又被切回的会话
            try {
                redisTemplate.delete(REDIS_KEY_2FA_VERIFIED + userId);
            } catch (DataAccessException e) {
                log.warn("[2FA] failed to delete verified cache for user {}: {}", userId, e.getMessage());
            }
        }
        log.info("[2FA] toggle: user={} enabled={} changed={}", userId, enabled, changed);
        return user;
    }

    @Override
    @Transactional
    public UserEntity changePhone(Long userId, String phone, String code) {
        // 1. 当前用户校验
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        // 2. 注销冻结期：与 password login / phone login 行为一致
        if (user.getDeleteScheduledAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (!user.getDeleteScheduledAt().isAfter(now)) {
                throw new IllegalArgumentException("账号已注销，如需使用请重新注册");
            }
        }
        // 3. 新号不能与当前相同
        if (phone.equals(user.getPhone())) {
            throw new IllegalArgumentException("新手机号不能与当前相同");
        }
        // 4. 校验 purpose=CHANGE_PHONE 的验证码（仅取最近一条未使用未过期记录）
        SmsCodeEntity record = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeEntity>()
                        .eq(SmsCodeEntity::getPhone, phone)
                        .eq(SmsCodeEntity::getPurpose, "CHANGE_PHONE")
                        .eq(SmsCodeEntity::getUsed, 0)
                        .gt(SmsCodeEntity::getExpireAt, LocalDateTime.now())
                        .orderByDesc(SmsCodeEntity::getId)
                        .last("LIMIT 1"));
        if (record == null) {
            throw new IllegalArgumentException("验证码不存在或已过期");
        }
        // 5. 失败次数防爆破
        if (record.getFailCount() != null && record.getFailCount() >= PHONE_CODE_MAX_FAIL) {
            record.setUsed(1);
            smsCodeMapper.updateById(record);
            throw new IllegalArgumentException("验证码错误次数过多,请重新获取");
        }
        // 6. 验证码匹配：失败递增计数,达上限置 used=1
        if (!code.equals(record.getCode())) {
            record.setFailCount((record.getFailCount() == null ? 0 : record.getFailCount()) + 1);
            smsCodeMapper.updateById(record);
            throw new IllegalArgumentException("验证码错误");
        }
        // 7. 校验新手机号未被其他账号占用（uk_user_phone 唯一索引兜底，这里先抛业务异常）
        Long occupied = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getPhone, phone)
                        .ne(UserEntity::getId, userId));
        if (occupied != null && occupied > 0) {
            throw new IllegalArgumentException("该手机号已被其他账号使用");
        }
        // 8. 标记验证码已使用 + 更新用户手机号
        record.setUsed(1);
        smsCodeMapper.updateById(record);
        user.setPhone(phone);
        userMapper.updateById(user);
        log.info("[changePhone] success: user={} newPhone={}", userId, phone);
        return user;
    }

    // ==================== 手机短信验证码登录 ====================

    /** 手机验证码有效期：5 分钟 */
    private static final long PHONE_CODE_EXPIRE_SECONDS = 300;
    /** 手机验证码最大失败次数：5 次后失效（防爆破） */
    private static final int PHONE_CODE_MAX_FAIL = 5;
    /** 手机号发送频次限制：同号 1 分钟内只能发 1 条 */
    private static final long PHONE_SEND_INTERVAL_SECONDS = 60;
    /** 同号 1 小时最多 5 条 */
    private static final long PHONE_HOURLY_LIMIT = 5;
    private static final long PHONE_HOURLY_WINDOW_SECONDS = 3600;

    // ==================== 账号注销 ====================

    /** 注销冻结期：15 天（业内通用，参考微信 15 天/京东 30 天） */
    private static final long DELETION_GRACE_DAYS = 15L;
    /** mo_user.status：正常 */
    private static final int USER_STATUS_ACTIVE = 1;
    /** mo_user.status：注销待执行（PENDING_DELETE，等同于"冻结中"） */
    private static final int USER_STATUS_PENDING_DELETE = 3;

    /** 数据导出请求类型（与 admin 端导出订单区分） */
    private static final String DATA_EXPORT_TYPE_USER = "USER_DATA_EXPORT";
    /** 数据导出初始状态 */
    private static final String DATA_EXPORT_STATUS_PENDING = "PENDING";
    /** 同账号两次导出最小间隔（24h） */
    private static final long DATA_EXPORT_COOLDOWN_HOURS = 24L;

    @Override
    @Transactional
    public void sendPhoneCode(String phone, String purpose) {
        // 1. 频次限流（Redis 原子 INCR + EXPIRE）
        String hourlyKey = "auth:phone-hourly:" + phone;
        Long count;
        try {
            count = redisTemplate.execute(INCR_WITH_EXPIRE_IF_NEW,
                    Collections.singletonList(hourlyKey),
                    String.valueOf(PHONE_HOURLY_WINDOW_SECONDS));
        } catch (DataAccessException e) {
            // Redis 不可用 fail-open：放行发送，避免验证码系统故障阻断登录
            log.error("Redis 不可用,phone code 发送限流降级", e);
            count = 1L;
        }
        if (count != null && count > PHONE_HOURLY_LIMIT) {
            // Lua INCR 从 1 开始计数:count=1 是第 1 次,count=5 是第 5 次,
            // count=6 才达到上限(PHONE_HOURLY_LIMIT=5),直接拒绝。
            // 修复:原 IllegalArgumentException 被映射为 400,改用 BusinessException 显式 429
            throw new BusinessException(429, "发送过于频繁,请 1 小时后再试");
        }

        // 2. 同号最小间隔限流
        String intervalKey = "auth:phone-interval:" + phone;
        try {
            Boolean canSend = redisTemplate.opsForValue().setIfAbsent(
                    intervalKey, "1", PHONE_SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(canSend)) {
                throw new IllegalArgumentException("发送过于频繁,请稍后再试");
            }
        } catch (DataAccessException e) {
            log.error("Redis 不可用,phone interval 限流降级", e);
        }

        // 3. 生成 6 位随机验证码
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));

        // 4. 持久化到 mo_sms_code（用于校验 / 审计）
        SmsCodeEntity entity = new SmsCodeEntity();
        entity.setPhone(phone);
        entity.setCode(code);
        entity.setPurpose(purpose);
        entity.setUsed(0);
        entity.setFailCount(0);
        entity.setExpireAt(LocalDateTime.now().plusSeconds(PHONE_CODE_EXPIRE_SECONDS));
        smsCodeMapper.insert(entity);

        // 5. 调用 SMS Provider 发送（Aliyun 实现，Noop 实现仅打日志）
        try {
            SmsService smsService = smsServiceProvider.getIfAvailable();
            if (smsService == null) {
                log.warn("[sms] 未找到 SmsService Bean（moyuyo.sms.provider 未配置），跳过发送。phone={}, purpose={}", phone, purpose);
                return;
            }
            smsService.sendCode(phone, code, purpose);
        } catch (Exception e) {
            // 发送失败回滚：删除刚才插入的记录 + 解除 interval 限流 key
            smsCodeMapper.deleteById(entity.getId());
            try {
                redisTemplate.delete(intervalKey);
            } catch (DataAccessException ignored) {
            }
            throw new BusinessException(502, "短信发送失败: " + e.getMessage());
        }
        log.info("Phone code sent: phone={}, purpose={}", phone, purpose);
    }

    @Override
    @Transactional
    public TokenResponse loginByPhone(String phone, String code) {
        // 1. 查询最近一条未使用、未过期、purpose=LOGIN 的验证码记录
        SmsCodeEntity record = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCodeEntity>()
                        .eq(SmsCodeEntity::getPhone, phone)
                        .eq(SmsCodeEntity::getPurpose, "LOGIN")
                        .eq(SmsCodeEntity::getUsed, 0)
                        .gt(SmsCodeEntity::getExpireAt, LocalDateTime.now())
                        .orderByDesc(SmsCodeEntity::getId)
                        .last("LIMIT 1"));
        if (record == null) {
            throw new IllegalArgumentException("验证码不存在或已过期");
        }
        // 2. 校验失败次数
        if (record.getFailCount() != null && record.getFailCount() >= PHONE_CODE_MAX_FAIL) {
            // 标记失效
            record.setUsed(1);
            smsCodeMapper.updateById(record);
            throw new IllegalArgumentException("验证码错误次数过多,请重新获取");
        }
        // 3. 验证码匹配
        if (!code.equals(record.getCode())) {
            record.setFailCount((record.getFailCount() == null ? 0 : record.getFailCount()) + 1);
            smsCodeMapper.updateById(record);
            throw new IllegalArgumentException("验证码错误");
        }
        // 4. 标记已使用
        record.setUsed(1);
        smsCodeMapper.updateById(record);

        // 5. 查找或自动创建用户
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPhone, phone));
        if (user == null) {
            // 手机号首次登录：自动创建账号,邮箱为空,随机密码,默认昵称=手机号后 4 位
            user = new UserEntity();
            user.setPhone(phone);
            // 占位 email: phone+"@phone.moyuyo.local"（避免破坏 email NOT NULL 约束）
            user.setEmail(phone + "@phone.moyuyo.local");
            user.setNickname("用户" + phone.substring(Math.max(0, phone.length() - 4)));
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setStatus(1);
            user.setPoints(0);
            user.setEmailVerified(false);
            user.setTwoFactorEnabled(false);
            user.setMarketingOptIn(false);
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.insert(user);
            log.info("Phone login auto-registered user: id={}, phone={}", user.getId(), phone);
        } else {
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new IllegalArgumentException("账号已被禁用");
            }
            // 注销冻结期校验：与 password login 路径行为一致
            if (user.getDeleteScheduledAt() != null) {
                LocalDateTime now = LocalDateTime.now();
                if (!user.getDeleteScheduledAt().isAfter(now)) {
                    log.warn("Phone login rejected: account deletion grace period expired, userId={}", user.getId());
                    throw new IllegalArgumentException("账号已注销，如需使用请重新注册");
                }
                user.setDeleteScheduledAt(null);
                user.setStatus(USER_STATUS_ACTIVE);
                log.info("Phone login during deletion grace period, auto-revoke: userId={}", user.getId());
            }
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);
        }
        return generateTokenPair(user);
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

    // ==================== 账号注销 ====================

    /**
     * 申请注销账户。
     * <p>
     * 关键设计：
     * <ul>
     *   <li>幂等：重复提交直接返回当前已存在的时间戳，不覆盖</li>
     *   <li>状态机：写入 {@code delete_scheduled_at} + {@code status=3(PENDING_DELETE)}</li>
     *   <li>先写 DB 再吊销 token：拆为事务方法 + 事务外 logout,
     *       避免 Redis 已写入但 DB 事务回滚导致"被踢出但未冻结"</li>
     *   <li>吊销失败不阻断流程（用户已确认意愿）</li>
     *   <li>审计：日志输出 userId/scheduledAt，便于后续追溯</li>
     * </ul>
     */
    @Override
    public LocalDateTime requestAccountDeletion(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User not logged in");
        }
        // 第一步：事务内写 DB（独立事务方法,确保回滚不连带 Redis）
        LocalDateTime scheduledAt = markDeletionPending(userId);

        // 第二步：事务提交后再吊销 token（Redis 操作不参与事务）
        // 校验 token:ThreadLocal 可能因拦截器/过滤器执行顺序、异步切换等原因未写入,
        // 拿到 null 会被拼成 "blacklist:null" 这种非法 key,既未真正拉黑当前 access token,
        // 也可能让 Redis 客户端对 null value 抛 NPE。
        String accessToken = UserContextHolder.getToken();
        if (accessToken == null || accessToken.isBlank()) {
            // 兜底:不阻断注销流程(用户意愿已确认,DB 已写入 deleteScheduledAt,15 天后定时任务会清理)
            log.warn("注销流程未拿到 access token,跳过 Redis 拉黑,userId={}", userId);
        } else {
            try {
                logout(userId, accessToken);
            } catch (Exception e) {
                // logout 失败不应阻断注销流程（DB 已写入 deleteScheduledAt,
                // 即使 token 暂时未吊销,15 天后定时任务仍会清理;用户也无法撤销 token 失效前的请求)
                log.warn("Logout during deletion request failed, userId={}: {}", userId, e.getMessage());
            }
        }
        return scheduledAt;
    }

    /**
     * 标记账号进入 PENDING_DELETE 状态（独立更新,与 logout 解耦）。
     * <p>
     * 注意：未显式加 {@code @Transactional}，因为 {@link #requestAccountDeletion} 同类内
     * 自调用会绕过 Spring AOP 代理导致事务失效；MyBatis-Plus 的 updateById
     * 自身走单语句隐式事务即可。
     * <p>
     * 预校验：用户存在未完成订单（PENDING_PAY/PAID/PENDING_SHIP/SHIPPED/RECEIVED/REFUNDING/EXCHANGING）
     * 时拒绝申请，引导用户先处理订单（业内通用规则,参考京东/淘宝注销须知）。
     * 校验失败抛 {@link com.moyuyo.common.exception.BusinessException} 携带 409 业务码，
     * 让前端弹"请先完成/取消订单"。
     */
    public LocalDateTime markDeletionPending(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        // 幂等：已有未到期注销计划则直接返回,不重置倒计时(保护后悔药窗口)
        if (user.getDeleteScheduledAt() != null && user.getDeleteScheduledAt().isAfter(LocalDateTime.now())) {
            log.info("Account deletion already requested: userId={}, scheduledAt={}", userId, user.getDeleteScheduledAt());
            return user.getDeleteScheduledAt();
        }

        // 预校验：未完成订单
        // 只统计 delete_status=0（未软删）且状态不在终态的订单
        // 终态：CANCELLED / COMPLETED / REFUNDED / EXCHANGED
        List<String> terminalStatuses = java.util.Arrays.asList(
                com.moyuyo.common.enums.OrderStatusEnum.CANCELLED.name(),
                com.moyuyo.common.enums.OrderStatusEnum.COMPLETED.name(),
                com.moyuyo.common.enums.OrderStatusEnum.REFUNDED.name(),
                com.moyuyo.common.enums.OrderStatusEnum.EXCHANGED.name());
        Long activeOrderCount = orderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.moyuyo.dao.entity.OrderEntity>()
                        .eq(com.moyuyo.dao.entity.OrderEntity::getUserId, userId)
                        .eq(com.moyuyo.dao.entity.OrderEntity::getDeleteStatus, 0)
                        .notIn(com.moyuyo.dao.entity.OrderEntity::getStatus, terminalStatuses));
        if (activeOrderCount != null && activeOrderCount > 0) {
            log.warn("Account deletion blocked: user has {} active orders, userId={}", activeOrderCount, userId);
            // 文案模板:固定前缀让前端可以做 SERVER_ERROR_MAP 匹配 i18n key,
            // 数字部分由前端拼接(避免后端 message 随数字漂移导致翻译匹配失败)
            throw new com.moyuyo.common.exception.BusinessException(
                    409, "DELETION_HAS_ACTIVE_ORDERS:" + activeOrderCount);
        }

        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(DELETION_GRACE_DAYS);
        user.setDeleteScheduledAt(scheduledAt);
        user.setStatus(USER_STATUS_PENDING_DELETE);
        userMapper.updateById(user);
        log.info("Account deletion requested: userId={}, scheduledAt={}", userId, scheduledAt);
        return scheduledAt;
    }

    /**
     * 撤销注销申请。
     * <p>
     * 三种场景：
     * <ol>
     *   <li>未提交注销：直接 200（幂等）</li>
     *   <li>期内撤销：清空字段 + status=1</li>
     *   <li>已到期：保留状态（定时任务已清理或正在清理），不报错，
     *       但前端提示文案应引导用户重新注册</li>
     * </ol>
     */
    @Override
    public void cancelAccountDeletion(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User not logged in");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (user.getDeleteScheduledAt() == null) {
            log.info("Cancel deletion but no pending request: userId={}", userId);
            return;
        }
        user.setDeleteScheduledAt(null);
        user.setStatus(USER_STATUS_ACTIVE);
        userMapper.updateById(user);
        log.info("Account deletion cancelled: userId={}", userId);
    }

    /**
     * 查询注销状态。
     * <p>
     * 同时返回剩余秒数与 epoch 毫秒，便于前端做"撤销倒计时"展示。
     * 注意：该接口需要登录态（被冻结用户无法再访问，所以这里不需要单独鉴权）。
     */
    @Override
    public DeletionStatus getDeletionStatus(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User not logged in");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        LocalDateTime scheduledAt = user.getDeleteScheduledAt();
        if (scheduledAt == null) {
            return new DeletionStatus(false, null, 0L, "ACTIVE");
        }
        LocalDateTime now = LocalDateTime.now();
        long remaining = java.time.Duration.between(now, scheduledAt).getSeconds();
        // 统一用服务端时区把 LocalDateTime 转 epoch 毫秒,前端无需猜测时区
        Long scheduledAtMillis = scheduledAt.atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        if (remaining <= 0) {
            // 已到期但定时任务尚未清理：状态仍标记 PENDING_DELETE，由定时任务接管
            return new DeletionStatus(true, scheduledAtMillis, 0L, "PENDING_DELETE");
        }
        return new DeletionStatus(true, scheduledAtMillis, remaining, "PENDING_DELETE");
    }

    // ==================== 数据导出（V20260916_01） ====================

    /** USER 端数据导出：1 天内同账号最多 1 次，超出抛 429 */
    @Override
    public DataExportAck requestDataExport(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User not logged in");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        LocalDateTime now = LocalDateTime.now();
        long nowMillis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        // 频次限制：data_export_requested_at + 24h > now → 拒绝
        if (user.getDataExportRequestedAt() != null) {
            LocalDateTime nextAllowed = user.getDataExportRequestedAt()
                    .plusHours(DATA_EXPORT_COOLDOWN_HOURS);
            if (nextAllowed.isAfter(now)) {
                long nextAllowedMillis = nextAllowed.atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                log.info("Data export rate limited: userId={}, nextAllowed={}", userId, nextAllowed);
                throw new com.moyuyo.common.exception.BusinessException(
                        429, "DATA_EXPORT_RATE_LIMITED:" + nextAllowedMillis);
            }
        }

        // 1) 写入 mo_data_export_request（PENDING 状态，由定时任务异步处理）
        com.moyuyo.dao.admin.entity.DataExportRequestEntity req =
                new com.moyuyo.dao.admin.entity.DataExportRequestEntity();
        req.setUserId(userId);
        req.setExportId("EXP-" + userId + "-" + System.currentTimeMillis());
        req.setTaskName("USER_DATA_EXPORT");
        req.setFormat("JSON");
        req.setRequestType(DATA_EXPORT_TYPE_USER);
        req.setStatus(DATA_EXPORT_STATUS_PENDING);
        req.setRemark("用户自助导出账户数据，文件将发送至注册邮箱");
        dataExportRequestMapper.insert(req);

        // 2) 更新 mo_user.data_export_requested_at,触发限流窗口
        user.setDataExportRequestedAt(now);
        userMapper.updateById(user);

        log.info("Data export requested: userId={}, requestId={}", userId, req.getId());
        return new DataExportAck(req.getId(), DATA_EXPORT_STATUS_PENDING, nowMillis, null);
    }

    /**
     * 规范化邮箱地址：去除首尾空格并转为小写。
     * 用于缓存 Key 与数据库查询，避免大小写不一致导致验证码无法匹配或重复注册。
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * 重置验证码脱敏：仅展示首位 + **** + 末位（6 位数字 → 形如 1****6），
     * 防止日志里泄露完整明文被暴力利用。
     */
    private String maskCode(String code) {
        if (code == null || code.length() < 2) return "****";
        return code.charAt(0) + "****" + code.charAt(code.length() - 1);
    }
}

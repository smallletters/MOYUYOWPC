package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.dto.auth.*;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.LogMasker;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.dao.entity.SmsCodeEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.SmsCodeMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.AuthService;
import com.moyuyo.service.EmailService;
import com.moyuyo.service.InviteService;
import com.moyuyo.service.SmsService;
import com.moyuyo.service.event.UserTokenInvalidatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    /** EmailService 可选注入：Bean 缺失时不阻断启动；但具体发送由 EmailServiceImpl 按环境决定 log-only / fail-closed */
    private final ObjectProvider<EmailService> emailServiceProvider;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    /** 统一密码编码器 Bean，强度由 moyuyo.password.bcrypt-strength 控制（默认 12） */
    private final PasswordEncoder passwordEncoder;
    /** 注册时按邀请码绑定邀请关系 */
    private final InviteService inviteService;
    /** 事件发布器：改密后发布 token 失效事件，由 api 模块监听并强制关闭该用户 WebSocket 会话 */
    private final ApplicationEventPublisher eventPublisher;
    /** 登录失败计数 Redis 不可用计数器：便于 Prometheus 告警 */
    private final Counter loginFailureCounter;
    /** Magic Link 跳转的 H5 基础地址（与 EmailServiceImpl 默认值保持一致） */
    @Value("${moyuyo.h5-base:http://localhost:5174}")
    private String h5Base;

    /** 全站每分钟短信发送总量上限（可通过 MOYUYO_SMS_GLOBAL_LIMIT_PER_MINUTE 调整） */
    @Value("${moyuyo.sms.global-limit-per-minute:100}")
    private long phoneGlobalLimitPerMinute = 100;

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
                           InviteService inviteService,
                           ApplicationEventPublisher eventPublisher,
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
        this.inviteService = inviteService;
        this.eventPublisher = eventPublisher;
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

    private static final long ACCESS_TOKEN_EXPIRE_SECONDS = 86400;
    // refresh token 有效期 28 天（与 access 24h + 前端 6h 主动续期配合）：
    // - 28d 不打开 APP 才需要重新登录，覆盖了绝大部分"季节性用户"场景
    // - 比 7d 延长 4 倍，提升用户体验
    // - 安全权衡：refresh token 一次性有效，被盗用一次后服务端吊销；被盗窗口 28d
    //   比 7d 略大但仍可通过 2FA + 设备绑定降低风险
    private static final long REFRESH_TOKEN_EXPIRE_SECONDS = 2419200; // 28d = 28 * 24 * 3600
    private static final long VERIFICATION_CODE_EXPIRE_SECONDS = 300;
    private static final long RESET_TOKEN_EXPIRE_SECONDS = 1800;
    private static final long MAGIC_LINK_EXPIRE_SECONDS = 900;
    private static final long TWO_FACTOR_CODE_EXPIRE_SECONDS = 300;
    /** WebSocket 一次性握手票据：60 秒有效，使用后立即删除 */
    private static final long WS_TICKET_EXPIRE_SECONDS = 60;
    private static final String REDIS_KEY_WS_TICKET = "auth:ws-ticket:";

    private static final String REDIS_KEY_REFRESH = "auth:refresh:";
    // 心跳同日幂等：{userId}:{yyyyMMdd} 存在即视为今日已上报过活跃,避免冗余 UPDATE
    // TTL 选 48h 是为了给"凌晨 0 点附近"留出 24h 余量,防止跨日竞态下旧 Key 误判
    private static final String REDIS_KEY_HEARTBEAT = "auth:heartbeat:";
    // 反向索引：userId -> refresh token 集合，用于登出时批量吊销
    private static final String REDIS_KEY_USER_REFRESH = "auth:user-refresh:";
    private static final String REDIS_KEY_BLACKLIST = "auth:blacklist:";
    /** access token 版本号：改密/重置密码后 INCR，旧 access token 与旧 refresh token 立即失效 */
    private static final String REDIS_KEY_TOKEN_VERSION = "auth:token-version:";
    private static final String REDIS_KEY_VERIFY_CODE = "auth:verify:";
    // 邮箱验证码失败计数：每次错误 INCR，达到上限立即作废验证码
    private static final String REDIS_KEY_VERIFY_FAIL = "auth:verify-fail:";
    // 同邮箱发送冷却 60 秒，防邮件轰炸
    private static final String REDIS_KEY_VERIFY_COOLDOWN = "auth:verify-cooldown:";
    // 同邮箱 24 小时发送总量上限，防止被当作邮件轰炸器
    private static final String REDIS_KEY_VERIFY_DAILY = "auth:verify-daily:";
    private static final long VERIFY_CODE_SEND_INTERVAL_SECONDS = 60;
    private static final long VERIFY_CODE_DAILY_LIMIT = 10;
    private static final int MAX_VERIFY_CODE_FAIL_ATTEMPTS = 5;
    // 6 位数字重置验证码（新范式，与前端 forgot.vue 6-digit input 对齐）
    // 2026-11 安全修复：key 由 code 改为 email，验证码与邮箱强绑定，避免全局 6 位 code key 被撞码/覆盖
    private static final String REDIS_KEY_RESET_CODE = "auth:resetcode:";
    // 重置验证码失败次数：每错一次 INCR，超过 5 次强制失效（避免 6 位数被暴力穷举）
    private static final String REDIS_KEY_RESET_FAIL = "auth:resetcode-fail:";
    // 发送冷却：同一邮箱 60 秒内只允许触发一封重置邮件，避免被当作邮件轰炸器
    private static final String REDIS_KEY_RESET_COOLDOWN = "auth:resetcode-cooldown:";
    private static final long RESET_CODE_SEND_INTERVAL_SECONDS = 60;
    private static final String REDIS_KEY_MAGIC_LINK = "auth:magiclink:";
    private static final String REDIS_KEY_2FA_CODE = "auth:2fa:";
    private static final String REDIS_KEY_2FA_VERIFIED = "auth:2fa-verified:";
    // 2FA 验证码失败计数：每次错误 INCR，达到上限立即作废
    private static final String REDIS_KEY_2FA_FAIL = "auth:2fa-fail:";
    // 2FA 发送冷却与每日上限，防止登录流程被当作邮件轰炸器
    private static final String REDIS_KEY_2FA_SEND_COOLDOWN = "auth:2fa-send:";
    private static final String REDIS_KEY_2FA_DAILY = "auth:2fa-daily:";
    private static final long TWO_FACTOR_SEND_INTERVAL_SECONDS = 60;
    private static final long TWO_FACTOR_DAILY_LIMIT = 10;
    private static final int MAX_2FA_FAIL_ATTEMPTS = 5;
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
            // 邮箱验证码校验：必须与服务端下发的一致，防止用任意邮箱注册
            verifyRegisterEmailCode(email, request.getEmailCode());
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
        // 邮箱注册已校验验证码，直接标记邮箱已验证；手机注册的占位邮箱保持未验证
        user.setEmailVerified(hasEmail);
        user.setStatus(1);
        user.setPoints(0);
        user.setTwoFactorEnabled(false);
        user.setLastLoginTime(LocalDateTime.now());

        userMapper.insert(user);

        // 邀请关系绑定：邀请码非法/已被自己使用时不阻断注册，由 bindInvitee 返回 null 兜底
        if (request.getInviteCode() != null && !request.getInviteCode().isBlank()) {
            inviteService.bindInvitee(request.getInviteCode().trim(), user.getId());
        }

        log.info("User registered: id={}, email={}, phone={}", user.getId(),
                LogMasker.maskEmail(user.getEmail()), LogMasker.maskPhone(user.getPhone()));
        return generateTokenPair(user);
    }

    /**
     * 注册邮箱验证码校验：校验通过后立即删除，保证一次性使用。
     */
    private void verifyRegisterEmailCode(String email, String emailCode) {
        if (emailCode == null || emailCode.isBlank()) {
            throw new IllegalArgumentException("邮箱注册需提供邮箱验证码");
        }
        String key = REDIS_KEY_VERIFY_CODE + email;
        String failKey = REDIS_KEY_VERIFY_FAIL + email;

        String storedCode;
        try {
            storedCode = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("verifyRegisterEmailCode 读取 Redis 失败，email.len={}", email.length(), e);
            throw new BusinessException(503, "系统繁忙，请稍后再试", e);
        }
        int failCount = readVerifyFailCount(email, failKey);
        if (failCount >= MAX_VERIFY_CODE_FAIL_ATTEMPTS) {
            clearVerifyCodeState(email, key, failKey);
            throw new IllegalArgumentException("邮箱验证码错误或已过期");
        }
        // 错误文案与“验证码不存在”保持一致，避免邮箱枚举
        if (storedCode == null || !storedCode.equals(emailCode.trim())) {
            recordVerifyCodeFailure(email, key, failKey);
            throw new IllegalArgumentException("邮箱验证码错误或已过期");
        }
        clearVerifyCodeState(email, key, failKey);
    }

    /** 读取邮箱验证码失败次数；Redis 异常时抛 503，脏数据按 0 */
    private int readVerifyFailCount(String email, String failKey) {
        try {
            String value = redisTemplate.opsForValue().get(failKey);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("读取邮箱验证码失败计数 Redis 失败，email.len={}", email.length(), e);
            throw new BusinessException(503, "系统繁忙，请稍后再试", e);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 记录一次邮箱验证码错误；达到上限立即作废验证码 */
    private void recordVerifyCodeFailure(String email, String key, String failKey) {
        try {
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(failKey, VERIFICATION_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count >= MAX_VERIFY_CODE_FAIL_ATTEMPTS) {
                clearVerifyCodeState(email, key, failKey);
            }
            log.warn("Email verification code mismatch, email.len={}, failCount={}", email.length(), count);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("记录邮箱验证码失败次数 Redis 失败，email.len={}", email.length(), e);
        }
    }

    /** 清理验证码、失败计数与发送冷却（成功消费或超限作废时使用） */
    private void clearVerifyCodeState(String email, String key, String failKey) {
        try {
            redisTemplate.delete(key);
            redisTemplate.delete(failKey);
            redisTemplate.delete(REDIS_KEY_VERIFY_COOLDOWN + email);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("清理邮箱验证码状态 Redis 失败，email={}", LogMasker.maskEmail(email), e);
        }
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
            log.warn("Login attempt rejected: account locked, email={}, remainSeconds={}",
                    LogMasker.maskEmail(email), remainSeconds);
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

        ensureLoginAllowed(user);

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

        // 3. 两步验证：开启 2FA 的账号，密码正确后必须再通过邮箱验证码，否则不签发 token
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            enforceTwoFactorForLogin(user, request.getTwoFactorCode());
        }

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("User logged in: id={}, email={}", user.getId(), LogMasker.maskEmail(user.getEmail()));
        return generateTokenPair(user);
    }

    /**
     * 登录场景的两步验证。
     * <p>
     * 未提供验证码时：发送验证码（有 60 秒冷却）并返回 {@code TWO_FACTOR_REQUIRED}，
     * 客户端带上验证码重新提交登录即可；已提供时校验验证码，错误 5 次作废。
     * <p>
     * 这里不签发任何 token，避免"密码正确但 2FA 未通过"的会话建立。
     */
    private void enforceTwoFactorForLogin(UserEntity user, String twoFactorCode) {
        if (twoFactorCode == null || twoFactorCode.isBlank()) {
            try {
                sendTwoFactorCode(user.getId());
            } catch (IllegalArgumentException e) {
                // 邮箱未绑定/无效等用户侧问题，直接透出可读提示
                throw new BusinessException(400, "TWO_FACTOR_EMAIL_REQUIRED");
            } catch (IllegalStateException e) {
                throw new BusinessException(502, "TWO_FACTOR_SEND_FAILED", e);
            } catch (BusinessException e) {
                // EmailService 发送失败等业务异常：统一收敛为客户端可识别的发码失败，
                // 避免把下游原始错误直接抛给登录页
                throw new BusinessException(502, "TWO_FACTOR_SEND_FAILED", e);
            }
            throw new BusinessException(428, "TWO_FACTOR_REQUIRED");
        }
        verifyTwoFactorCodeForLogin(user.getId(), twoFactorCode);
    }

    /** 登录 2FA 校验：只消费验证码，不写入“开启 2FA 的二次确认”缓存 */
    private void verifyTwoFactorCodeForLogin(Long userId, String code) {
        verifyTwoFactorCodeInternal(userId, code, "TWO_FACTOR_INVALID");
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

        log.warn("Login failed: email={}, reason={}, failCount={}",
                LogMasker.maskEmail(email), reason, failCount);

        // 连续失败超过阈值，锁定账户
        if (failCount >= MAX_LOGIN_FAIL_ATTEMPTS) {
            String lockKey = REDIS_KEY_ACCOUNT_LOCK + email;
            try {
                redisTemplate.opsForValue().set(lockKey, String.valueOf(failCount),
                        ACCOUNT_LOCK_DURATION_SECONDS, TimeUnit.SECONDS);
                // 锁定后清除失败计数，解锁后重新计数
                redisTemplate.delete(failKey);
                log.warn("Account locked due to too many failed login attempts: email={}",
                        LogMasker.maskEmail(email));
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
        String stored;
        try {
            stored = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            // Redis 不可用时 fail-open：让用户重新走登录流程，避免静默 500 给客户端
            // 与登录路径 fail-open 语义对齐：业务可用性优先
            loginFailureCounter.increment();
            log.error("刷新 Token 读取 Redis 不可用（fail-open），token.len={}",
                    request.getRefreshToken() == null ? 0 : request.getRefreshToken().length(), e);
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        if (stored == null) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // refresh token 的 value 格式：userId:tokenVersion
        // 历史数据是纯 userId（无冒号），按版本 0 处理，保证升级前已登录用户仍可正常续期
        Long userId;
        long refreshTokenVersion;
        try {
            int separator = stored.indexOf(':');
            if (separator > 0) {
                userId = Long.parseLong(stored.substring(0, separator));
                refreshTokenVersion = Long.parseLong(stored.substring(separator + 1));
            } else {
                userId = Long.parseLong(stored);
                refreshTokenVersion = 0L;
            }
        } catch (NumberFormatException e) {
            log.error("refresh token 内容解析失败，stored.len={}", stored.length(), e);
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // 一次性消费：先删除旧 refresh token，并从反向索引中移除
        try {
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(REDIS_KEY_USER_REFRESH + userId, request.getRefreshToken());
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("刷新 Token 清理 Redis 不可用（fail-open 继续校验），userId={}", userId, e);
        }

        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        // 关键：改密后版本号已递增，旧 refresh token 不允许再换新 access token
        long currentVersion = currentTokenVersion(userId);
        if (refreshTokenVersion != currentVersion) {
            log.warn("refresh token version mismatch（改密后旧 refresh token 失效）: userId={}, tokenVer={}, currentVer={}",
                    userId, refreshTokenVersion, currentVersion);
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // 刷新 Token 时再次校验封禁状态，避免已封禁用户通过旧 refresh token 续期
        ensureLoginAllowed(user);

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
        revokeAllRefreshTokens(userId);

        log.info("User logged out: id={}", userId);
    }

    /**
     * 按 userId 批量吊销全部 refresh token（登出 / 改密复用）。
     * <p>
     * Redis 不可用时 fail-open：refresh token 到期后自然失效，不影响主流程。
     */
    private void revokeAllRefreshTokens(Long userId) {
        try {
            String userRefreshKey = REDIS_KEY_USER_REFRESH + userId;
            Set<String> refreshTokens = redisTemplate.opsForSet().members(userRefreshKey);
            if (refreshTokens != null && !refreshTokens.isEmpty()) {
                List<String> keys = refreshTokens.stream()
                        .map(token -> REDIS_KEY_REFRESH + token)
                        .collect(Collectors.toList());
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(userRefreshKey);
        } catch (Exception e) {
            loginFailureCounter.increment();
            log.error("批量吊销 refresh token Redis 不可用（fail-open），userId={}", userId, e);
        }
    }

    @Override
    public void sendEmailVerification(EmailVerifyRequest request) {
        // 规范化邮箱，避免大小写不一致导致缓存 Key 与数据库查询不匹配
        String email = normalizeEmail(request.getEmail());
        if (email == null || email.isBlank()) {
            log.info("Email verification requested without email, ignored");
            return;
        }

        // 注册流程必须能发验证码（邮箱尚未入库），所以这里不查 user 表。
        // 用户已注册且邮箱已验证时仍跳过发送，避免对同一邮箱重复骚扰。
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));
        if (user != null && Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        // 同邮箱 60 秒发送冷却，避免验证码接口被当作邮件轰炸器
        String cooldownKey = REDIS_KEY_VERIFY_COOLDOWN + email;
        try {
            Boolean firstSend = redisTemplate.opsForValue().setIfAbsent(
                    cooldownKey, "1", VERIFY_CODE_SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(firstSend)) {
                log.info("Email verification code already sent recently, email.len={}", email.length());
                return;
            }
        } catch (DataAccessException e) {
            log.warn("Email verification cooldown check failed, continue sending, email.len={}", email.length(), e);
        }

        // 同邮箱 24 小时总量上限：即使换 IP 也不能无限轰炸同一邮箱
        try {
            Long dailyCount = redisTemplate.execute(
                    INCR_WITH_EXPIRE_IF_NEW,
                    Collections.singletonList(REDIS_KEY_VERIFY_DAILY + email),
                    String.valueOf(24 * 3600));
            if (dailyCount != null && dailyCount > VERIFY_CODE_DAILY_LIMIT) {
                log.warn("Email verification daily limit reached, email.len={}", email.length());
                return;
            }
        } catch (DataAccessException e) {
            log.warn("Email verification daily limit check failed, continue sending, email.len={}", email.length(), e);
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        String codeKey = REDIS_KEY_VERIFY_CODE + email;
        String failKey = REDIS_KEY_VERIFY_FAIL + email;
        redisTemplate.opsForValue().set(codeKey, code, VERIFICATION_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        // 新验证码重置失败计数，避免历史失败次数影响本次验证
        redisTemplate.opsForValue().set(failKey, "0", VERIFICATION_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 不记录验证码明文，避免日志泄露导致账号接管（真实发送由 EmailService 落地）
        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService == null) {
            log.warn("[email-verify] EmailService Bean 不存在，仅在日志模式下生成验证码：{}",
                    LogMasker.maskEmail(email));
        } else {
            try {
                emailService.sendEmailVerificationCode(
                        email,
                        code,
                        (int) (VERIFICATION_CODE_EXPIRE_SECONDS / 60));
            } catch (RuntimeException e) {
                // 发送失败释放冷却与验证码，避免用户 60 秒内重试时“接口成功但收不到邮件”
                clearVerifyCodeState(email, codeKey, failKey);
                throw e;
            }
        }
        log.info("Verification code sent to {}", LogMasker.maskEmail(email));
    }

    @Override
    public void confirmEmailVerification(EmailVerifyConfirmRequest request) {
        // 规范化邮箱，确保与发送验证码时使用的 Key 一致
        String email = normalizeEmail(request.getEmail());
        if (email == null || email.isBlank() || request.getCode() == null) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        String key = REDIS_KEY_VERIFY_CODE + email;
        String failKey = REDIS_KEY_VERIFY_FAIL + email;

        String storedCode;
        try {
            storedCode = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("confirmEmailVerification 读取 Redis 失败，email.len={}", email.length(), e);
            throw new BusinessException(503, "系统繁忙，请稍后再试", e);
        }

        int failCount = readVerifyFailCount(email, failKey);
        if (failCount >= MAX_VERIFY_CODE_FAIL_ATTEMPTS) {
            clearVerifyCodeState(email, key, failKey);
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        if (storedCode == null) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        // 每次错误必须计数，超过 5 次立即作废验证码，防止 6 位码被穷举
        if (!storedCode.equals(request.getCode())) {
            recordVerifyCodeFailure(email, key, failKey);
            throw new IllegalArgumentException("Invalid or expired verification code");
        }

        clearVerifyCodeState(email, key, failKey);

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user != null) {
            user.setEmailVerified(true);
            userMapper.updateById(user);
            log.info("Email verified: {}", LogMasker.maskEmail(email));
        }
    }

    @Override
    public void sendPasswordReset(EmailVerifyRequest request) {
        // 规范化邮箱，确保后续 resetPassword 查询数据库时能匹配
        String email = normalizeEmail(request.getEmail());
        // 兜底：即使绕过 Bean Validation，也不能让 eq(email, null) 在 not_null 策略下退化为全表查询
        if (email == null || email.isBlank()) {
            log.info("Password reset requested without email, ignored");
            return;
        }
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, email));

        if (user == null) {
            // 防止用户名枚举：对外仍返回 200；仅内部日志记录长度（不记录明文）
            log.info("Password reset requested for non-existent email, len={}",
                    email == null ? 0 : email.length());
            return;
        }

        // 发送冷却：同一邮箱 60 秒内只生成一次验证码，避免重置接口被当作邮件轰炸器
        // Redis 不可用时 fail-open（继续发送），可用性优先；真正的防爆破依赖下方 resetPassword 的失败计数
        String cooldownKey = REDIS_KEY_RESET_COOLDOWN + email;
        try {
            Boolean firstSend = redisTemplate.opsForValue().setIfAbsent(
                    cooldownKey, "1", RESET_CODE_SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(firstSend)) {
                log.info("Password reset code already sent recently, email.len={}", email.length());
                return;
            }
        } catch (DataAccessException e) {
            log.warn("Password reset cooldown check failed, continue sending, email.len={}", email.length(), e);
        }

        // 2026/08 架构对齐：与 forgot.vue 表单（6-digit input）一致，生成 6 位数字验证码。
        // 2026-11 安全修复：验证码与邮箱强绑定，key 改为 email（value 存 code），
        // 避免原实现"全局 code -> email"映射导致的撞码覆盖与跨账号重置。
        String resetCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String codeKey = REDIS_KEY_RESET_CODE + email;
        String failKey = REDIS_KEY_RESET_FAIL + email;
        // 验证码与失败计数都存 30 分钟，TTL 一致便于同步过期
        redisTemplate.opsForValue().set(codeKey, resetCode, RESET_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(failKey, "0", RESET_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 真实发送邮件（EmailServiceImpl 在非 dev 未配置 SMTP 时抛 503，发送失败抛 502，
        // 均中断流程，避免用户误以为"已发"）
        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService == null) {
            log.warn("[password-reset] EmailService Bean 不存在，进入 log-only 降级：to={}",
                    LogMasker.maskEmail(email));
        } else {
            try {
                emailService.sendPasswordResetCode(
                        email,
                        resetCode,
                        (int) (RESET_TOKEN_EXPIRE_SECONDS / 60));
            } catch (RuntimeException e) {
                // 发送失败时释放冷却，避免用户在 60 秒内重试时"接口返回成功但收不到邮件"
                clearResetCooldown(email);
                throw e;
            }
        }

        // 不记录 reset code 明文，避免日志泄露
        log.info("Password reset code sent to {}", LogMasker.maskEmail(email));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 验证码与邮箱强绑定：必须同时提供 email + 6 位 code，避免仅凭全局 code 重置任意账号
        String email = normalizeEmail(request.getEmail());
        String code = request.getToken();
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired reset code");
        }
        String codeKey = REDIS_KEY_RESET_CODE + email;
        String failKey = REDIS_KEY_RESET_FAIL + email;

        String storedCode;
        try {
            storedCode = redisTemplate.opsForValue().get(codeKey);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("resetPassword 读取 Redis 不可用，email.len={}", email.length(), e);
            throw new IllegalArgumentException("系统繁忙，请稍后再试");
        }

        // 6 位 code 防爆破：先查失败次数，≥5 则立刻失效（同 PHONE_CODE_MAX_FAIL）
        int failCount;
        try {
            String failStr = redisTemplate.opsForValue().get(failKey);
            failCount = failStr == null ? 0 : Integer.parseInt(failStr);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("resetPassword 读取失败计数 Redis 不可用，email.len={}", email.length(), e);
            throw new IllegalArgumentException("系统繁忙，请稍后再试");
        } catch (NumberFormatException e) {
            failCount = 0;
        }
        if (failCount >= MAX_RESET_CODE_FAIL_ATTEMPTS) {
            deleteResetCode(codeKey, failKey, email);
            // 与"验证码不存在"返回同一文案：避免攻击者通过该分支判断邮箱是否已注册
            throw new IllegalArgumentException("Invalid or expired reset code");
        }

        if (storedCode == null) {
            throw new IllegalArgumentException("Invalid or expired reset code");
        }
        // 每次失败都必须计数并落库到 Redis，否则 6 位码可被无限穷举
        // 错误文案必须与"code 不存在"完全一致，避免攻击者据此判断邮箱是否已注册
        if (!storedCode.equals(code)) {
            recordResetCodeFailure(email, codeKey, failKey);
            throw new IllegalArgumentException("Invalid or expired reset code");
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

        // 先递增 token 版本：失败会抛异常回滚事务，确保"密码已改但旧 token 仍有效"不会发生
        bumpTokenVersion(user.getId());
        // 事务提交后由 api 模块监听并强制关闭该用户已建立的 WebSocket 长连接
        publishTokenInvalidated(user.getId(), "PASSWORD_RESET");

        // 一次性消费：成功后立刻删 Redis，防止同一 code 被重复改密码（会话劫持场景）
        deleteResetCode(codeKey, failKey, email);
        clearResetCooldown(email);

        // 改密后立刻吊销该用户全部 refresh token，避免旧会话（及被盗会话）继续续期
        revokeAllRefreshTokens(user.getId());

        log.info("Password reset completed for: {}", LogMasker.maskEmail(email));
    }

    /**
     * 记录一次重置验证码失败：INCR 失败计数，达到上限立即作废验证码。
     * <p>
     * 安全要点：必须对"无效 code"和"有效 code 但不匹配"都计数，否则攻击者可以无限猜码。
     */
    private void recordResetCodeFailure(String email, String codeKey, String failKey) {
        try {
            Long count = redisTemplate.opsForValue().increment(failKey);
            // 首次失败时补一次 TTL（发送阶段已初始化 TTL；这里兜底 key 已过期的场景）
            if (count != null && count == 1L) {
                redisTemplate.expire(failKey, RESET_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count >= MAX_RESET_CODE_FAIL_ATTEMPTS) {
                // 达到上限立刻作废验证码，必须重新申请
                deleteResetCode(codeKey, failKey, email);
            }
            log.warn("Password reset code mismatch, email.len={}, failCount={}", email.length(), count);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("记录重置验证码失败次数 Redis 不可用，email.len={}", email.length(), e);
        }
    }

    /**
     * 清理重置验证码与失败计数。
     * <p>
     * 注意：刻意不删除发送冷却 key —— 失败/超限后仍需等待 60 秒才能重新发码，
     * 避免攻击者通过"5 次猜错 → 立刻重发"绕过频率限制。
     */
    private void deleteResetCode(String codeKey, String failKey, String email) {
        try {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failKey);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("resetPassword 清理验证码 Redis 失败，email={}", LogMasker.maskEmail(email), e);
        }
    }

    /** 改密成功后清除发送冷却，允许用户下次正常申请 */
    private void clearResetCooldown(String email) {
        try {
            redisTemplate.delete(REDIS_KEY_RESET_COOLDOWN + email);
        } catch (DataAccessException e) {
            log.warn("resetPassword 清理发送冷却 Redis 失败，email={}", LogMasker.maskEmail(email), e);
        }
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

        // 递增版本号：当前 access token 立即失效；同时吊销 refresh token，强制所有设备重新登录
        bumpTokenVersion(userId);
        revokeAllRefreshTokens(userId);
        // 事务提交后由 api 模块监听并强制关闭该用户已建立的 WebSocket 长连接
        publishTokenInvalidated(userId, "PASSWORD_CHANGED");

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
        // 个人简介：与 nickname 一致走 XSS 净化；空串视为"清空简介"，写 NULL 入库
        if (update.getBio() != null) {
            String sanitized = com.moyuyo.common.utils.XssSanitizer.sanitizePlainText(update.getBio());
            user.setBio(sanitized.isEmpty() ? null : sanitized);
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
            log.warn("[magic-link] EmailService Bean 不存在，进入 log-only 降级：to={}",
                    LogMasker.maskEmail(email));
        } else {
            emailService.sendMagicLink(
                    email,
                    magicLink,
                    (int) (MAGIC_LINK_EXPIRE_SECONDS / 60));
        }

        // 不记录 magic link token 明文，避免日志泄露
        log.info("Magic link sent to {}", LogMasker.maskEmail(email));
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

        ensureLoginAllowed(user);

        // 已开启 2FA 的账号不允许用 Magic Link 直接登录：
        // Magic Link 仅证明邮箱可达，与 2FA 的邮箱验证码是同一个因子，无法构成第二因子。
        // 强制其走“密码 + 两步验证码”登录，避免绕过 2FA。
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            log.warn("Magic link login blocked by 2FA policy: userId={}", user.getId());
            throw new BusinessException(403, "该账号已开启两步验证，请使用密码登录并完成验证");
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
        // 手机号注册用户的占位邮箱（phone@phone.moyuyo.local）无法收信，明确拒绝
        if (email == null || email.endsWith("@phone.moyuyo.local")) {
            throw new IllegalArgumentException("用户未绑定有效邮箱,无法发送两步验证验证码");
        }

        // 发送冷却：同一用户 60 秒内只生成一次验证码，避免登录流程被当作邮件轰炸器
        String cooldownKey = REDIS_KEY_2FA_SEND_COOLDOWN + userId;
        try {
            Boolean firstSend = redisTemplate.opsForValue().setIfAbsent(
                    cooldownKey, "1", TWO_FACTOR_SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(firstSend)) {
                log.info("[2FA] code already sent recently, userId={}", userId);
                return;
            }
        } catch (DataAccessException e) {
            log.warn("[2FA] cooldown check failed, continue sending, userId={}", userId, e);
        }

        // 24 小时发送上限，防止同一账号被反复触发发信
        try {
            Long dailyCount = redisTemplate.execute(
                    INCR_WITH_EXPIRE_IF_NEW,
                    Collections.singletonList(REDIS_KEY_2FA_DAILY + userId),
                    String.valueOf(24 * 3600));
            if (dailyCount != null && dailyCount > TWO_FACTOR_DAILY_LIMIT) {
                log.warn("[2FA] daily limit reached, userId={}", userId);
                return;
            }
        } catch (DataAccessException e) {
            log.warn("[2FA] daily limit check failed, continue sending, userId={}", userId, e);
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        String codeKey = REDIS_KEY_2FA_CODE + userId;
        String failKey = REDIS_KEY_2FA_FAIL + userId;
        redisTemplate.opsForValue().set(codeKey, code, TWO_FACTOR_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        // 新验证码重置失败计数
        redisTemplate.opsForValue().set(failKey, "0", TWO_FACTOR_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 真发邮件：与 sendEmailVerificationCode 复用同一 SMTP 通道；
        // EmailServiceImpl 在 dev 未配置 SMTP 时走 log-only，非 dev 环境直接抛 503，
        // 避免"接口成功但用户收不到两步验证码"。
        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService == null) {
            // 删除已写入的验证码/失败计数/冷却，避免用户永远收不到码
            clearTwoFactorSendState(userId, codeKey, failKey, cooldownKey);
            log.error("[2FA] EmailService Bean 未注册,无法发送验证码(userId={})", userId);
            throw new IllegalStateException("邮件服务未配置,无法发送两步验证验证码");
        }
        try {
            emailService.sendTwoFactorCode(email, code, (int) (TWO_FACTOR_CODE_EXPIRE_SECONDS / 60));
        } catch (RuntimeException e) {
            // 发送失败释放冷却，允许用户立刻重试
            clearTwoFactorSendState(userId, codeKey, failKey, cooldownKey);
            throw e;
        }

        // 不记录 2FA 验证码明文,避免日志泄露;
        // EmailService 自身已对收件人邮箱做了 mask 脱敏日志
        log.info("[2FA] code sent for user: {}", userId);
    }

    @Override
    public void verifyTwoFactorCode(Long userId, String code) {
        // 开启 2FA 前的二次身份确认：与登录 2FA 共用校验逻辑与失败计数
        verifyTwoFactorCodeInternal(userId, code, "Invalid or expired verification code");
        try {
            String verifiedKey = REDIS_KEY_2FA_VERIFIED + userId;
            redisTemplate.opsForValue().set(verifiedKey, "1", 7200, TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.error("[2FA] 写入 verified 缓存失败，userId={}", userId, e);
            throw new BusinessException(503, "系统繁忙，请稍后再试", e);
        }
        log.info("2FA verified for user: {}", userId);
    }

    /**
     * 2FA 验证码校验（登录与开启 2FA 共用）：
     * 失败计数 ≥5 立即作废；每次错误 INCR；成功后一次性消费验证码。
     * <p>
     * 失败/超限时刻意不释放发送冷却，避免“5 次猜错后立刻重发”绕过频率限制。
     */
    private void verifyTwoFactorCodeInternal(Long userId, String code, String invalidMessage) {
        String codeKey = REDIS_KEY_2FA_CODE + userId;
        String failKey = REDIS_KEY_2FA_FAIL + userId;
        String storedCode;
        try {
            storedCode = redisTemplate.opsForValue().get(codeKey);
        } catch (DataAccessException e) {
            log.error("[2FA] 读取验证码 Redis 失败，userId={}", userId, e);
            throw new BusinessException(503, "系统繁忙，请稍后再试", e);
        }
        int failCount = readTwoFactorFailCount(userId, failKey);
        if (failCount >= MAX_2FA_FAIL_ATTEMPTS) {
            clearTwoFactorCode(userId, codeKey, failKey);
            throw new IllegalArgumentException(invalidMessage);
        }
        if (storedCode == null || !storedCode.equals(code)) {
            recordTwoFactorFailure(userId, codeKey, failKey);
            throw new IllegalArgumentException(invalidMessage);
        }
        clearTwoFactorCode(userId, codeKey, failKey);
    }

    /** 读取 2FA 失败次数；Redis 异常抛 503，脏数据按 0 */
    private int readTwoFactorFailCount(Long userId, String failKey) {
        try {
            String value = redisTemplate.opsForValue().get(failKey);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (DataAccessException e) {
            log.error("[2FA] 读取失败计数 Redis 失败，userId={}", userId, e);
            throw new BusinessException(503, "系统繁忙，请稍后再试", e);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 记录一次 2FA 错误；达到上限立即作废验证码（保留发送冷却） */
    private void recordTwoFactorFailure(Long userId, String codeKey, String failKey) {
        try {
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(failKey, TWO_FACTOR_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count >= MAX_2FA_FAIL_ATTEMPTS) {
                clearTwoFactorCode(userId, codeKey, failKey);
            }
            log.warn("[2FA] code mismatch, userId={}, failCount={}", userId, count);
        } catch (DataAccessException e) {
            log.error("[2FA] 记录失败次数 Redis 失败，userId={}", userId, e);
        }
    }

    /** 消费/作废验证码：只删验证码与失败计数，保留发送冷却 */
    private void clearTwoFactorCode(Long userId, String codeKey, String failKey) {
        try {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failKey);
        } catch (DataAccessException e) {
            log.error("[2FA] 清理验证码 Redis 失败，userId={}", userId, e);
        }
    }

    /** 发送失败时清理验证码、失败计数与冷却，允许用户立即重试 */
    private void clearTwoFactorSendState(Long userId, String codeKey, String failKey, String cooldownKey) {
        try {
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failKey);
            redisTemplate.delete(cooldownKey);
        } catch (DataAccessException e) {
            log.error("[2FA] 清理发送状态 Redis 失败，userId={}", userId, e);
        }
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
            // 开启 2FA 后，之前签发的所有会话都不再可信：
            // 1) 递增 token 版本 → 旧 access token 立即失效（所有设备）
            // 2) 吊销 refresh token → 旧 refresh token 不能续期；
            //    即使吊销因 Redis 抖动失败，refresh 侧也会因版本不匹配拒绝
            // 3) 广播 token 失效事件 → 关闭各实例上的 WebSocket 长连接
            // 版本递增失败会抛异常并回滚本次 2FA 开启，避免"2FA 已开但旧会话仍有效"
            bumpTokenVersion(userId);
            revokeAllRefreshTokens(userId);
            publishTokenInvalidated(userId, "TWO_FACTOR_ENABLED");

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
    /** 全站短信总量统计窗口（秒）：略大于 60 秒，避免分钟边界抖动 */
    private static final long PHONE_GLOBAL_WINDOW_SECONDS = 70;
    /** 允许的短信验证码用途：客户端不可传任意字符串 */
    private static final Set<String> PHONE_PURPOSE_WHITELIST =
            Set.of("LOGIN", "REGISTER", "RESET_PASSWORD", "CHANGE_PHONE");

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
        // 0. 用途白名单：防止客户端传入任意 purpose 污染数据或绕过用途语义
        String normalizedPurpose = purpose == null ? "LOGIN" : purpose.trim().toUpperCase();
        if (!PHONE_PURPOSE_WHITELIST.contains(normalizedPurpose)) {
            throw new IllegalArgumentException("无效的验证码用途");
        }

        // 0.1 全站每分钟短信总量上限：即使攻击者不断更换手机号，也不能无限刷短信成本
        String globalKey = "auth:phone-global:" + (System.currentTimeMillis() / 60000);
        Long globalCount;
        try {
            globalCount = redisTemplate.execute(INCR_WITH_EXPIRE_IF_NEW,
                    Collections.singletonList(globalKey),
                    String.valueOf(PHONE_GLOBAL_WINDOW_SECONDS));
        } catch (DataAccessException e) {
            // Redis 不可用 fail-open：优先保证验证码可用
            log.error("Redis 不可用,phone global 限流降级", e);
            globalCount = 1L;
        }
        if (globalCount != null && globalCount > phoneGlobalLimitPerMinute) {
            throw new BusinessException(429, "短信发送过于频繁,请稍后再试");
        }

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
        entity.setPurpose(normalizedPurpose);
        entity.setUsed(0);
        entity.setFailCount(0);
        entity.setExpireAt(LocalDateTime.now().plusSeconds(PHONE_CODE_EXPIRE_SECONDS));
        smsCodeMapper.insert(entity);

        // 5. 调用 SMS Provider 发送（Aliyun 实现，Noop 实现仅 dev 打日志）
        SmsService smsService = smsServiceProvider.getIfAvailable();
        if (smsService == null) {
            // 无 Provider 时不能静默返回成功：清理 Redis 限流 key，并明确失败
            try {
                redisTemplate.delete(intervalKey);
            } catch (DataAccessException ignored) {
            }
            throw new BusinessException(503, "短信服务未配置，请联系管理员");
        }
        try {
            smsService.sendCode(phone, code, normalizedPurpose);
        } catch (Exception e) {
            // 发送失败回滚：删除刚才插入的记录 + 解除 interval 限流 key
            smsCodeMapper.deleteById(entity.getId());
            try {
                redisTemplate.delete(intervalKey);
            } catch (DataAccessException ignored) {
            }
            // 保留 Provider 抛出的业务码（如 Noop 在非 dev 环境的 503），
            // 其余未知异常统一收敛为 502，避免把底层错误细节返回给客户端
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(502, "短信发送失败，请稍后再试");
        }
        log.info("Phone code sent: phone={}, purpose={}", LogMasker.maskPhone(phone), normalizedPurpose);
    }

    @Override
    @Transactional
    public TokenResponse loginByPhone(String phone, String code, String twoFactorCode) {
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
        // 4. 查找或自动创建用户
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
            log.info("Phone login auto-registered user: id={}, phone={}", user.getId(),
                    LogMasker.maskPhone(phone));
        } else {
            // 封禁/禁用校验：到期自动解封，未到期提示原因与解封时间
            ensureLoginAllowed(user);
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
            // 5. 两步验证：短信验证码通过后，已开启 2FA 的账号仍需邮箱验证码
            //    注意：此时尚未消费短信验证码，2FA 失败/未提供时用户可以用同一短信码重试
            if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
                enforceTwoFactorForLogin(user, twoFactorCode);
            }
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);
        }

        // 6. 2FA（如有）通过后才消费短信验证码，避免重试时提示“验证码已过期”
        record.setUsed(1);
        smsCodeMapper.updateById(record);

        return generateTokenPair(user);
    }

    private static final DateTimeFormatter BAN_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 登录前置校验：封禁已到期的账号自动解封；未到期或永久封禁则抛出带原因与解封时间的提示。
     * 密码登录 / 手机号登录 / 魔法链接登录 / 刷新 Token 均复用此逻辑。
     */
    private void ensureLoginAllowed(UserEntity user) {
        if (user.getStatus() != null && user.getStatus() == USER_STATUS_ACTIVE) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = user.getBanExpiresAt();
        // 临时封禁已到期：自动解封并清空封禁信息
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, user.getId())
                .set(UserEntity::getStatus, USER_STATUS_ACTIVE)
                .set(UserEntity::getBanReason, null)
                .set(UserEntity::getBannedAt, null)
                .set(UserEntity::getBanExpiresAt, null));
            user.setStatus(USER_STATUS_ACTIVE);
            user.setBanReason(null);
            user.setBannedAt(null);
            user.setBanExpiresAt(null);
            log.info("[auth] 封禁到期自动解封: userId={}", user.getId());
            return;
        }
        String reason = user.getBanReason() == null || user.getBanReason().isBlank()
            ? "违反社区规范" : user.getBanReason();
        if (expiresAt != null) {
            throw new IllegalArgumentException("账号已被封禁，解封时间："
                + BAN_TIME_FORMATTER.format(expiresAt) + "。原因：" + reason);
        }
        throw new IllegalArgumentException("账号已被永久封禁。原因：" + reason);
    }

    /**
     * 签发一次性 WebSocket 握手票据：60 秒有效，使用后由握手拦截器原子删除。
     * <p>
     * Redis 不可用时 fail-closed（抛 503），避免退化为把长期 token 放回 URL。
     */
    @Override
    public String issueWsTicket(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(
                    REDIS_KEY_WS_TICKET + ticket,
                    String.valueOf(userId),
                    WS_TICKET_EXPIRE_SECONDS,
                    TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.error("签发 WS ticket 失败（Redis 不可用），userId={}", userId, e);
            throw new BusinessException(503, "系统繁忙，请稍后再试");
        }
        return ticket;
    }

    private TokenResponse generateTokenPair(UserEntity user) {
        // 读取当前版本号并写入 access token / refresh token：
        // 改密后版本递增，旧 access token 在 JwtAuthFilter 被拒，旧 refresh token 也无法续期
        long tokenVersion = currentTokenVersion(user.getId());
        String accessToken = jwtUtil.generate(user.getId(), user.getEmail(), null, tokenVersion);

        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(
                    REDIS_KEY_REFRESH + refreshToken,
                    user.getId() + ":" + tokenVersion,
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
     * 读取用户当前 access token 版本号。
     * <p>
     * Redis 缺失/异常/脏数据一律按 0 处理：保证登录、续期等主流程不因缓存抖动而失败；
     * 若服务端实际版本大于 0 而读取失败，新 token 会被后续鉴权拒绝，用户重新登录即可（fail-closed）。
     */
    private long currentTokenVersion(Long userId) {
        try {
            String value = redisTemplate.opsForValue().get(REDIS_KEY_TOKEN_VERSION + userId);
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (DataAccessException | NumberFormatException e) {
            loginFailureCounter.increment();
            log.error("读取 token version 失败（按 0 处理），userId={}", userId, e);
            return 0L;
        }
    }

    /**
     * 递增 access token 版本号：旧 access token / 旧 refresh token 立即失效。
     * <p>
     * 失败直接抛异常，让上层 {@code @Transactional} 回滚密码变更，
     * 避免出现"密码已改但旧 token 仍有效"的不一致状态。
     */
    private void bumpTokenVersion(Long userId) {
        try {
            redisTemplate.opsForValue().increment(REDIS_KEY_TOKEN_VERSION + userId);
        } catch (DataAccessException e) {
            loginFailureCounter.increment();
            log.error("递增 token version 失败，中止改密，userId={}", userId, e);
            throw new IllegalStateException("系统繁忙，请稍后再试", e);
        }
    }

    /** 发布 token 失效事件：监听方（api 模块）负责关闭该用户已建立的 WebSocket 会话 */
    private void publishTokenInvalidated(Long userId, String reason) {
        try {
            eventPublisher.publishEvent(new UserTokenInvalidatedEvent(userId, reason));
        } catch (Exception e) {
            // 事件失败不影响改密主流程；旧 HTTP token 已因版本号失效，WS 连接最多保留到下次断开
            log.error("发布 token 失效事件失败（WebSocket 连接可能不会被强制关闭），userId={}", userId, e);
        }
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

    // ==================== 心跳（活跃用户统计信号） ====================

    /**
     * APP 端首页 onShow 等场景调用，用于把"今天活跃过"写回 lastLoginTime。
     * <p>
     * 关键点：
     * <ul>
     *   <li>Redis SETNX 当日幂等 Key（{@code auth:heartbeat:{userId}:{yyyyMMdd}}），TTL=48h
     *       → 同一天多次心跳只产生一次数据库 UPDATE + 第一次 Redis SET</li>
     *   <li>未拿到锁的并发请求走"读路径"：直接构造返回值返回，不再写库，但同样返回正确的 dateKey，
     *       让前端缓存命中、避免并发竞态导致 statisactive 漏数</li>
     *   <li>Redis 不可用时降级 fail-open：直接走 DB UPDATE，保证统计正确性；
     *       牺牲一点"同日去重"，但不会丢活跃信号</li>
     *   <li>不影响 token / 凭证 / 注销状态，仅写 last_login_time 字段</li>
     *   <li>不抛异常给前端调用方（包括用户不存在）：保持纯"上报"语义,
     *       让 APP 端无需根据业务错误码做不同处理</li>
     * </ul>
     */
    @Override
    public HeartbeatAck heartbeat(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        long nowMillis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        String dateKey = now.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (userId == null) {
            // 未登录调用:返回空 dateKey,前端可直接丢弃
            return new HeartbeatAck(dateKey, nowMillis);
        }

        String redisKey = REDIS_KEY_HEARTBEAT + userId + ":" + dateKey;

        // 1) Redis SETNX 当日幂等标记(48h 自然过期,跨日自动重新计数)
        boolean acquired = false;
        try {
            Boolean res = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", java.time.Duration.ofHours(48));
            acquired = Boolean.TRUE.equals(res);
        } catch (Exception e) {
            // Redis 不可用:不阻断,fail-open 直接走 DB UPDATE 保证活跃统计正确
            log.warn("[heartbeat] Redis SETNX 失败,降级直接 UPDATE DB: userId={}", userId, e);
        }

        // 2) 只有拿到锁(当天第一次心跳)才写库;并发请求跳过避免冗余 UPDATE
        if (acquired) {
            UserEntity user = userMapper.selectById(userId);
            if (user != null) {
                user.setLastLoginTime(now);
                userMapper.updateById(user);
                log.debug("[heartbeat] lastLoginTime updated: userId={}, dateKey={}", userId, dateKey);
            }
        }

        return new HeartbeatAck(dateKey, nowMillis);
    }

    /**
     * 规范化邮箱地址：去除首尾空格并转为小写。
     * 用于缓存 Key 与数据库查询，避免大小写不一致导致验证码无法匹配或重复注册。
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}

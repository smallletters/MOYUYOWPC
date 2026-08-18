package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台 — 认证相关接口
 * 负责管理员登录/登出/信息查询，通过数据库验证用户
 */
@Tag(name = "管理后台 - 认证")
@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;
    private final StringRedisTemplate redisTemplate;
    /** 统一密码编码器 Bean，强度由 moyuyo.password.bcrypt-strength 控制 */
    private final PasswordEncoder passwordEncoder;

    // 与 JwtAuthFilter 中一致的 Token 黑名单前缀
    private static final String REDIS_KEY_BLACKLIST = "auth:blacklist:";
    // 管理员登录失败计数
    private static final String REDIS_KEY_ADMIN_LOGIN_FAIL = "auth:admin-login-fail:";
    // 管理员账户锁定 Key
    private static final String REDIS_KEY_ADMIN_LOCK = "auth:admin-lock:";
    // 管理员连续失败 3 次后锁定（高价值目标，更严格
    private static final int MAX_ADMIN_LOGIN_FAILS = 3;
    // 管理员失败计数窗口：30 分钟
    private static final long ADMIN_FAIL_WINDOW_SECONDS = 1800;
    // 管理员锁定时长：30 分钟
    private static final long ADMIN_LOCK_DURATION_SECONDS = 1800;

    @Operation(summary = "管理员登录（支持邮箱或用户名）")
    @PostMapping("/login")
    @RateLimiter(name = "adminAuthLogin", fallbackMethod = "loginRateLimitFallback")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Result.error(400, "邮箱和密码不能为空");
        }
        // 入参长度硬上限：避免攻击者用 1MB+ email/password 触发 BCrypt 高 CPU 验签 DoS
        if (email.length() > 254 || password.length() > 64) {
            return Result.error(400, "邮箱或密码长度超限");
        }

        // 规范化账号标识：邮箱或用户名统一去空格转小写（邮箱按邮箱规范，用户名也统一小写避免大小写不一致
        String accountKey = email.trim().toLowerCase();

        // 1. 检查管理员账号是否已锁定（Redis 不可用时 fail-open 放行，避免业务全瘫）
        String lockKey = REDIS_KEY_ADMIN_LOCK + accountKey;
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
                Long remainSeconds = redisTemplate.getExpire(lockKey, java.util.concurrent.TimeUnit.SECONDS);
                log.warn("Admin login rejected: account locked, account={}, remainSeconds={}", accountKey, remainSeconds);
                return Result.error(423, "登录失败次数过多，请 " + (remainSeconds != null ? remainSeconds / 60 : 30) + " 分钟后再试");
            }
        } catch (Exception e) {
            // Redis 不可用：记录 ERROR 日志但放行（fail-open），让 IP 限流 + BCrypt 验签作为兜底
            log.error("Admin login: Redis 不可用，跳过账号锁定检查（fail-open），account={}", accountKey, e);
        }

        // 优先按邮箱查找，再按用户名查找
        AdminUserEntity adminUser = adminUserMapper.selectOne(
            new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getEmail, email)
                .eq(AdminUserEntity::getStatus, "ACTIVE")
        );
        if (adminUser == null) {
            adminUser = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUserEntity>()
                    .eq(AdminUserEntity::getUsername, email)
                    .eq(AdminUserEntity::getStatus, "ACTIVE")
            );
        }

        if (adminUser == null) {
            // 用户不存在也记录失败（防止用户名枚举
            recordAdminLoginFailure(accountKey, "admin_not_found");
            return Result.error(401, "邮箱或密码错误");
        }

        // 使用 BCrypt 验证密码，强度由 moyuyo.password.bcrypt-strength 控制（与 AdminInitializer 一致）
        if (!passwordEncoder.matches(password, adminUser.getPassword())) {
            recordAdminLoginFailure(accountKey, "wrong_password");
            String failKey = REDIS_KEY_ADMIN_LOGIN_FAIL + accountKey;
            try {
                String failCountStr = redisTemplate.opsForValue().get(failKey);
                int failCount = failCountStr == null ? 0 : Integer.parseInt(failCountStr);
                if (failCount >= MAX_ADMIN_LOGIN_FAILS) {
                    return Result.error(423, "登录失败次数过多，请 30 分钟后再试");
                }
            } catch (Exception e) {
                // Redis 不可用时仅记录错误，跳过失败次数检查（fail-open）
                log.error("Admin login: Redis 不可用，跳过失败次数检查（fail-open），account={}", accountKey, e);
            }
            return Result.error(401, "邮箱或密码错误");
        }

        // 2. 登录成功，清除失败记录
        try {
            clearAdminLoginFailureRecords(accountKey);
        } catch (Exception e) {
            // Redis 不可用：跳过失败记录清理（fail-open），下次登录自然覆盖
            log.error("Admin login: Redis 不可用，跳过失败记录清理（fail-open），account={}", accountKey, e);
        }

        // 更新最后登录时间
        adminUser.setLastLoginTime(java.time.LocalDateTime.now());
        adminUserMapper.updateById(adminUser);

        Map<String, Object> data = new HashMap<>();
        // 管理端 token 携带角色，JwtAuthFilter 会校验 /api/admin/** 的角色
        data.put("token", jwtUtil.generate(adminUser.getId(), email, adminUser.getRole()));
        data.put("name", adminUser.getName());
        data.put("role", adminUser.getRole());
        return Result.success(data);
    }

    /** 记录管理员登录失败次数，达到阈值时锁定账号 */
    private void recordAdminLoginFailure(String accountKey, String reason) {
        String failKey = REDIS_KEY_ADMIN_LOGIN_FAIL + accountKey;
        try {
            Long failCount = redisTemplate.opsForValue().increment(failKey);
            // 每次失败重置窗口过期时间（滑动窗口
            redisTemplate.expire(failKey, ADMIN_FAIL_WINDOW_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

            log.warn("Admin login failed: account={}, reason={}, failCount={}", accountKey, reason, failCount);

            if (failCount != null && failCount >= MAX_ADMIN_LOGIN_FAILS) {
                String lockKey = REDIS_KEY_ADMIN_LOCK + accountKey;
                redisTemplate.opsForValue().set(lockKey, String.valueOf(failCount), ADMIN_LOCK_DURATION_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                redisTemplate.delete(failKey);
                log.warn("Admin account LOCKED due to too many failures: account={}", accountKey);
            }
        } catch (Exception e) {
            // Redis 不可用：仅记录错误日志，跳过失败计数（fail-open）
            // 安全性由 IP 限流 + BCrypt 验签共同保证；Redis 抖动期间接受弱爆破风险换取业务可用性
            log.error("Admin login: Redis 不可用，跳过失败计数（fail-open），account={}, reason={}", accountKey, reason, e);
        }
    }

    /** 管理员登录成功后清除失败计数和锁定标记 */
    private void clearAdminLoginFailureRecords(String accountKey) {
        redisTemplate.delete(REDIS_KEY_ADMIN_LOGIN_FAIL + accountKey);
        redisTemplate.delete(REDIS_KEY_ADMIN_LOCK + accountKey);
    }

    @Operation(summary = "管理员退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 将当前 token 加入黑名单（剩余有效期内不可再用）
        String token = UserContextHolder.getToken();
        if (token != null && !token.isBlank()) {
            Date expiration = jwtUtil.parse(token).getExpiration();
            long ttlMillis = expiration.getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                redisTemplate.opsForValue().set(REDIS_KEY_BLACKLIST + token, "1", Duration.ofMillis(ttlMillis));
            }
        }
        UserContextHolder.clear();
        return Result.success();
    }

    @Operation(summary = "获取管理员信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> adminInfo() {
        Long userId = UserContextHolder.getUserId();

        // 未认证用户，返回 401
        if (userId == null || userId <= 0) {
            return Result.error(401, "未授权，请先登录");
        }

        // 从数据库查询当前用户信息
        AdminUserEntity adminUser = adminUserMapper.selectById(userId);
        if (adminUser != null) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", adminUser.getName());
            info.put("email", adminUser.getEmail());
            info.put("role", adminUser.getRole());
            return Result.success(info);
        }

        // 用户不存在
        return Result.error(401, "用户不存在或已被禁用");
    }

    /** 管理员登录限流降级方法 */
    @SuppressWarnings("unused")
    private Result<Map<String, Object>> loginRateLimitFallback(Map<String, String> body, RequestNotPermitted e) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }
}

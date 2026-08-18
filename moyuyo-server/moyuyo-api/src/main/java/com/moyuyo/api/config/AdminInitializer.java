package com.moyuyo.api.config;

import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 应用启动时初始化超级管理员（仅当系统中不存在任何管理员时执行）。
 * <p>
 * 安全约束：
 * <ol>
 *   <li>账号密码从环境变量 ADMIN_USERNAME / ADMIN_EMAIL / ADMIN_PASSWORD 读取，不硬编码、不重置已有账号</li>
 *   <li>禁止使用弱用户名（admin / root / administrator / test / guest 等），必须显式设置 ADMIN_USERNAME</li>
 *   <li>密码长度至少 12 位，BCrypt 强度设为 12</li>
 *   <li>email 必须符合邮箱格式（防止配置错误写入垃圾数据）</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    /** OWASP 常见弱用户名黑名单：禁止用作超级管理员账号 */
    private static final Set<String> WEAK_USERNAMES = Set.of(
            "admin", "root", "administrator", "test", "guest",
            "user", "demo", "default", "manager", "sysadmin"
    );

    /** 邮箱格式校验：保证至少有一个 @ 和 . */
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final AdminUserMapper adminUserMapper;

    /** 统一从 PasswordEncoderConfig 注入，强度由 moyuyo.password.bcrypt-strength 控制 */
    private final PasswordEncoder passwordEncoder;

    /** 显式用户名：生产环境必须通过环境变量显式设置，禁止使用弱默认值 */
    @Value("${admin.username:}")
    private String adminUsername;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        // 仅查 ACTIVE 状态的管理员账号，避免 selectCount(null) 在生产大表上做全表扫描
        // 软删除的账号不计入（status=DELETED 等由业务侧更新）
        // 索引：mo_admin_user.status 已有普通索引（详见 V20260717_01__admin_new_tables.sql）
        Long count = adminUserMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdminUserEntity>()
                .eq("status", "ACTIVE")
        );
        if (count != null && count > 0) {
            log.info("已存在 ACTIVE 管理员账号，跳过初始化");
            return;
        }

        if (adminUsername.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("未配置 ADMIN_USERNAME / ADMIN_EMAIL / ADMIN_PASSWORD，且当前无管理员账号，跳过超级管理员初始化");
            return;
        }

        // 1. 拒绝弱用户名（OWASP Top 10 暴力破解首选目标）
        String lowerUsername = adminUsername.trim().toLowerCase();
        if (WEAK_USERNAMES.contains(lowerUsername)) {
            log.error("拒绝创建管理员：用户名 '{}' 在弱用户名黑名单中，请显式设置 ADMIN_USERNAME 为非弱名称", adminUsername);
            return;
        }

        // 2. 邮箱格式校验
        if (!EMAIL_PATTERN.matcher(adminEmail).matches()) {
            log.error("拒绝创建管理员：ADMIN_EMAIL 格式不合法：{}", adminEmail);
            return;
        }

        // 3. 密码强度校验：至少 12 位（OWASP 推荐）
        if (adminPassword.length() < 12) {
            log.error("拒绝创建管理员：ADMIN_PASSWORD 长度 {} 小于 12 位，请使用强密码", adminPassword.length());
            return;
        }

        // 创建超级管理员，BCrypt 强度由 moyuyo.password.bcrypt-strength 控制（默认 12）
        AdminUserEntity newAdmin = new AdminUserEntity();
        newAdmin.setUsername(adminUsername.trim());
        newAdmin.setEmail(adminEmail.trim());
        newAdmin.setName("Admin");
        newAdmin.setRole("SUPER_ADMIN");
        newAdmin.setPassword(passwordEncoder.encode(adminPassword));
        newAdmin.setStatus("ACTIVE");
        newAdmin.setCreateTime(LocalDateTime.now());
        adminUserMapper.insert(newAdmin);
        log.info("超级管理员 [{}] 已创建（用户名/密码来自环境变量）", adminEmail);
    }
}

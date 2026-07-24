package com.moyuyo.api.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应用启动时初始化/重置默认管理员密码
 * 确保管理员始终可以使用已知密码登录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final AdminUserMapper adminUserMapper;

    // 默认管理员列表（用户名 -> 密码）
    private static final String[][] DEFAULT_ADMINS = {
        {"admin",  "admin@moyuyo.com",  "Admin",    "SUPER_ADMIN"},
        {"wang",   "wang@moyuyo.com",   "小王",     "OPERATOR"},
        {"li",     "li@moyuyo.com",     "小李",     "CUSTOMER_SVC"},
        {"zhang",  "zhang@moyuyo.com",  "小张",     "FINANCE"},
    };
    // 默认密码
    private static final String DEFAULT_PASSWORD = "123456";

    @Override
    public void run(String... args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        for (String[] admin : DEFAULT_ADMINS) {
            String username = admin[0];
            String email = admin[1];
            String name = admin[2];
            String role = admin[3];

            // 按用户名查找已有管理员
            AdminUserEntity existing = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUserEntity>()
                    .eq(AdminUserEntity::getUsername, username)
            );

            if (existing != null) {
                // 已存在，重置密码
                String newHash = encoder.encode(DEFAULT_PASSWORD);
                existing.setPassword(newHash);
                existing.setStatus("ACTIVE");
                adminUserMapper.updateById(existing);
                log.info("管理员 [{}] 密码已重置为默认密码", username);
            } else {
                // 不存在，创建
                AdminUserEntity newAdmin = new AdminUserEntity();
                newAdmin.setUsername(username);
                newAdmin.setEmail(email);
                newAdmin.setName(name);
                newAdmin.setRole(role);
                newAdmin.setPassword(encoder.encode(DEFAULT_PASSWORD));
                newAdmin.setStatus("ACTIVE");
                newAdmin.setCreateTime(LocalDateTime.now());
                adminUserMapper.insert(newAdmin);
                log.info("管理员 [{}] 已创建", username);
            }
        }
        log.info("管理员初始化完成，默认密码: {}", DEFAULT_PASSWORD);
    }
}

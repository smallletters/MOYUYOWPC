package com.moyuyo.api.config;

import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminInitializer 单元测试
 * 重点验证安全约束：
 *   1. 拒绝弱用户名（admin/root/administrator/test 等 OWASP 黑名单）
 *   2. 拒绝邮箱格式不合法
 *   3. 拒绝密码长度 < 12
 *   4. 已有管理员时跳过初始化
 *   5. 环境变量缺失时跳过
 *   6. 合法配置时创建管理员
 */
@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private AdminUserMapper adminUserMapper;

    private AdminInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminInitializer(adminUserMapper);
        ReflectionTestUtils.setField(initializer, "adminUsername", "");
        ReflectionTestUtils.setField(initializer, "adminEmail", "");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");
    }

    @Test
    @DisplayName("已有管理员时跳过初始化，不创建新账号")
    void run_whenAdminExists_skipsInitialization() {
        when(adminUserMapper.selectCount(null)).thenReturn(1L);

        initializer.run();

        verify(adminUserMapper, never()).insert(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("环境变量缺失时跳过初始化")
    void run_whenEnvVarsMissing_skipsInitialization() {
        when(adminUserMapper.selectCount(null)).thenReturn(0L);
        // username/email/password 都是空

        initializer.run();

        verify(adminUserMapper, never()).insert(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("拒绝弱用户名 admin（OWASP Top 10）")
    void run_whenWeakUsername_rejectsAndDoesNotInsert() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "StrongPassword123!");

        when(adminUserMapper.selectCount(null)).thenReturn(0L);

        initializer.run();

        verify(adminUserMapper, never()).insert(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("拒绝弱用户名 root")
    void run_whenRootUsername_rejectsAndDoesNotInsert() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "root");
        ReflectionTestUtils.setField(initializer, "adminEmail", "root@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "StrongPassword123!");

        when(adminUserMapper.selectCount(null)).thenReturn(0L);

        initializer.run();

        verify(adminUserMapper, never()).insert(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("拒绝弱用户名 Administrator（大小写无关）")
    void run_whenAdministratorUsername_rejectsAndDoesNotInsert() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "Administrator");
        ReflectionTestUtils.setField(initializer, "adminEmail", "x@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "StrongPassword123!");

        when(adminUserMapper.selectCount(null)).thenReturn(0L);

        initializer.run();

        verify(adminUserMapper, never()).insert(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("拒绝邮箱格式不合法")
    void run_whenEmailInvalid_rejectsAndDoesNotInsert() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "valid_user");
        ReflectionTestUtils.setField(initializer, "adminEmail", "not-an-email");
        ReflectionTestUtils.setField(initializer, "adminPassword", "StrongPassword123!");

        when(adminUserMapper.selectCount(null)).thenReturn(0L);

        initializer.run();

        verify(adminUserMapper, never()).insert(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("拒绝密码长度小于 12")
    void run_whenPasswordTooShort_rejectsAndDoesNotInsert() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "valid_user");
        ReflectionTestUtils.setField(initializer, "adminEmail", "user@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "short");

        when(adminUserMapper.selectCount(null)).thenReturn(0L);

        initializer.run();

        verify(adminUserMapper, never()).insert(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("合法配置时创建管理员（BCrypt 加密）")
    void run_whenValidConfig_createsAdmin() {
        ReflectionTestUtils.setField(initializer, "adminUsername", "moyuyo_admin");
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@moyuyo.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "StrongPassword123!");

        when(adminUserMapper.selectCount(null)).thenReturn(0L);
        when(adminUserMapper.insert(any(AdminUserEntity.class))).thenAnswer(inv -> {
            AdminUserEntity entity = inv.getArgument(0);
            entity.setId(1L);
            return 1;
        });

        initializer.run();

        verify(adminUserMapper, times(1)).insert(any(AdminUserEntity.class));
    }
}

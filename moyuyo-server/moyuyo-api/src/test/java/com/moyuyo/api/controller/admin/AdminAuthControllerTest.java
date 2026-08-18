package com.moyuyo.api.controller.admin;

import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import com.moyuyo.common.security.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminAuthController 单元测试
 * 覆盖登录/登出/获取管理员信息三个核心接口，
 * 重点验证：参数校验、失败计数、账号锁定、Token 黑名单等安全逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminAuthController controller;

    private AdminUserEntity activeAdmin;

    @BeforeEach
    void setUp() {
        // 准备一个正常激活的管理员账号
        // 使用 BCrypt 真实哈希（明文密码 "password123"，强度 12），避免测试误判密码错误
        activeAdmin = new AdminUserEntity();
        activeAdmin.setId(1L);
        activeAdmin.setEmail("admin@example.com");
        activeAdmin.setUsername("admin_user");
        activeAdmin.setName("Admin");
        activeAdmin.setRole("SUPER_ADMIN");
        // 测试中 PasswordEncoder 是 Mock，直接 stub encode/matches 行为
        BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder(12);
        activeAdmin.setPassword(realEncoder.encode("password123"));
        activeAdmin.setStatus("ACTIVE");
        // Mock 的 passwordEncoder 走与 setUp 中相同的真实算法，避免 happy-path 误判
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenAnswer(inv -> {
            String raw = inv.getArgument(0);
            String hash = inv.getArgument(1);
            return realEncoder.matches(raw, hash);
        });
        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(inv -> realEncoder.encode(inv.getArgument(0)));

        // 默认：Redis 没有锁定 / 失败计数
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);
        // Spring Data Redis 4.x：expire/delete 均返回 Boolean（非 void），使用 doReturn 替代 doNothing
        lenient().doReturn(Boolean.TRUE).when(redisTemplate).expire(anyString(), anyLong(), any());
        lenient().doReturn(Boolean.TRUE).when(redisTemplate).delete(anyString());

        // 注入 private final 字段
        ReflectionTestUtils.setField(controller, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(controller, "adminUserMapper", adminUserMapper);
        ReflectionTestUtils.setField(controller, "redisTemplate", redisTemplate);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("登录 - 邮箱和密码为空时直接返回 400")
    void login_whenEmailOrPasswordBlank_returnsBadRequest() {
        Map<String, String> body = new HashMap<>();
        body.put("email", "");
        body.put("password", "");

        Result<Map<String, Object>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(400);
        // 不应触发任何 mapper / redis 查询
        verify(adminUserMapper, never()).selectOne(any());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("登录 - 邮箱格式不合法时：Controller 不做格式校验，按用户不存在返回 401（防止账号枚举）")
    void login_whenEmailFormatInvalid_returnsUnauthorized() {
        Map<String, String> body = new HashMap<>();
        body.put("email", "not-an-email");
        body.put("password", "123456");

        // 由于 AdminAuthController.login 不做邮箱格式校验，会走到 DB 查询并记录失败计数
        when(adminUserMapper.selectOne(any())).thenReturn(null);

        Result<Map<String, Object>> result = controller.login(body);

        // 期望 401 而非 400：未通过鉴权
        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("登录 - 用户不存在时也记录失败计数，防止账号枚举")
    void login_whenUserNotFound_recordsFailureAndReturnsUnauthorized() {
        Map<String, String> body = new HashMap<>();
        body.put("email", "ghost@example.com");
        body.put("password", "password123");

        when(adminUserMapper.selectOne(any())).thenReturn(null);

        Result<Map<String, Object>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(401);
        // 用户不存在也必须计数，防止用户名枚举
        verify(valueOps, times(1)).increment(anyString());
    }

    @Test
    @DisplayName("登录 - 账号已被锁定时直接返回 423，无需查 DB")
    void login_whenAccountLocked_returnsLocked() {
        Map<String, String> body = new HashMap<>();
        body.put("email", "admin@example.com");
        body.put("password", "anyPassword");

        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.getExpire(anyString(), any())).thenReturn(600L);

        Result<Map<String, Object>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(423);
        verify(adminUserMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("登录成功 - 返回 token 并清除失败计数")
    void login_whenSuccess_returnsTokenAndClearsFailure() {
        Map<String, String> body = new HashMap<>();
        body.put("email", "admin@example.com");
        body.put("password", "password123");

        when(adminUserMapper.selectOne(any())).thenReturn(activeAdmin);
        when(jwtUtil.generate(anyLong(), anyString(), anyString())).thenReturn("mock.jwt.token");

        Result<Map<String, Object>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsKeys("token", "name", "role");
        assertThat(result.getData().get("token")).isEqualTo("mock.jwt.token");
        assertThat(result.getData().get("role")).isEqualTo("SUPER_ADMIN");
        // 登录成功需清除失败计数
        verify(redisTemplate, times(2)).delete(anyString());
        // 最后登录时间需更新
        verify(adminUserMapper, times(1)).updateById(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("登录失败 - 密码错误时计数 +1")
    void login_whenPasswordWrong_recordsFailure() {
        Map<String, String> body = new HashMap<>();
        body.put("email", "admin@example.com");
        body.put("password", "wrongPassword");

        when(adminUserMapper.selectOne(any())).thenReturn(activeAdmin);

        Result<Map<String, Object>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(401);
        verify(valueOps, times(1)).increment(anyString());
        // 密码错误时不应签发 token
        verify(jwtUtil, never()).generate(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("登录 - 用户状态非 ACTIVE 时禁止登录")
    void login_whenUserDisabled_returnsUnauthorized() {
        Map<String, String> body = new HashMap<>();
        body.put("email", "admin@example.com");
        body.put("password", "password123");

        activeAdmin.setStatus("DISABLED");
        when(adminUserMapper.selectOne(any())).thenReturn(null); // 第一次按邮箱查不到

        Result<Map<String, Object>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(401);
        verify(valueOps, times(1)).increment(anyString());
    }

    @Test
    @DisplayName("me - 未登录时返回 401")
    void me_whenNoUserInContext_returnsUnauthorized() {
        Result<Map<String, Object>> result = controller.adminInfo();

        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("me - 正常返回管理员信息")
    void me_returnsAdminInfo() {
        UserContextHolder.setUserId(1L);
        UserContextHolder.setRole("SUPER_ADMIN");
        when(adminUserMapper.selectById(1L)).thenReturn(activeAdmin);

        Result<Map<String, Object>> result = controller.adminInfo();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().get("email")).isEqualTo("admin@example.com");
        assertThat(result.getData().get("role")).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("me - 用户已被删除时返回 401")
    void me_whenUserDeleted_returnsUnauthorized() {
        UserContextHolder.setUserId(999L);
        UserContextHolder.setRole("SUPER_ADMIN");
        when(adminUserMapper.selectById(999L)).thenReturn(null);

        Result<Map<String, Object>> result = controller.adminInfo();

        assertThat(result.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("logout - 未携带 token 时也能成功（无副作用）")
    void logout_whenNoToken_returnsSuccess() {
        Result<Void> result = controller.logout();

        assertThat(result.getCode()).isEqualTo(0);
        verify(redisTemplate, never()).opsForValue();
    }
}
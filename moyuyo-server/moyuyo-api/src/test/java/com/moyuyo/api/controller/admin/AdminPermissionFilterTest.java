package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.dao.admin.entity.AdminPermissionEntity;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminPermissionMapper;
import com.moyuyo.dao.admin.mapper.AdminRoleMapper;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口级 RBAC 集成测试
 * 验证 AdminPermissionFilter 按"角色 → 资源:操作"拦截管理端接口
 */
@AutoConfigureMockMvc
class AdminPermissionFilterTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    @Autowired
    private AdminPermissionMapper adminPermissionMapper;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @BeforeEach
    void setUpRoleData() {
        // AdminPermissionFilter 现在会校验管理员账号最新状态（禁用/删除/改角色），
        // 因此测试 token 对应的管理员必须真实存在于 mo_admin_user。
        insertAdmin(1L, "test_super_admin", "SUPER_ADMIN", "ACTIVE");
        insertAdmin(2L, "test_operator", "TEST_ROLE", "ACTIVE");
        insertAdmin(3L, "test_empty_role", "EMPTY_ROLE", "ACTIVE");
        insertAdmin(4L, "test_disabled_role", "DISABLED_ROLE", "ACTIVE");
        // 测试角色：仅授予 dashboard:view 权限
        AdminRoleEntity role = new AdminRoleEntity();
        role.setName("测试角色");
        role.setCode("TEST_ROLE");
        role.setStatus("ACTIVE");
        adminRoleMapper.insert(role);

        AdminPermissionEntity perm = new AdminPermissionEntity();
        perm.setRoleId(role.getId());
        perm.setResource("dashboard");
        perm.setAction("view");
        adminPermissionMapper.insert(perm);
    }

    @Test
    @DisplayName("已授权模块可访问（dashboard:view → 200）")
    void permittedModule_shouldPass() throws Exception {
        String token = "Bearer " + jwtUtil.generate(2L, "test@moyuyo.com", "TEST_ROLE");
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("未授权模块拒绝访问（无 rbac 权限 → 403）")
    void unpermittedModule_shouldReturn403() throws Exception {
        String token = "Bearer " + jwtUtil.generate(2L, "test@moyuyo.com", "TEST_ROLE");
        mockMvc.perform(get("/api/admin/rbac/roles")
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("未授权操作拒绝访问（仅 view 权限执行 create → 403）")
    void unpermittedAction_shouldReturn403() throws Exception {
        String token = "Bearer " + jwtUtil.generate(2L, "test@moyuyo.com", "TEST_ROLE");
        mockMvc.perform(post("/api/admin/push/create")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"channel\":\"NOTIFICATION\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("无任何权限配置的角色拒绝全部模块访问")
    void roleWithoutPerms_shouldReturn403() throws Exception {
        // 角色存在但无任何权限行
        AdminRoleEntity emptyRole = new AdminRoleEntity();
        emptyRole.setName("空权限角色");
        emptyRole.setCode("EMPTY_ROLE");
        emptyRole.setStatus("ACTIVE");
        adminRoleMapper.insert(emptyRole);

        String token = "Bearer " + jwtUtil.generate(3L, "empty@moyuyo.com", "EMPTY_ROLE");
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("已禁用角色拒绝访问")
    void disabledRole_shouldReturn403() throws Exception {
        AdminRoleEntity disabledRole = new AdminRoleEntity();
        disabledRole.setName("禁用角色");
        disabledRole.setCode("DISABLED_ROLE");
        disabledRole.setStatus("DISABLED");
        adminRoleMapper.insert(disabledRole);

        AdminPermissionEntity perm = new AdminPermissionEntity();
        perm.setRoleId(disabledRole.getId());
        perm.setResource("dashboard");
        perm.setAction("view");
        adminPermissionMapper.insert(perm);

        String token = "Bearer " + jwtUtil.generate(4L, "disabled@moyuyo.com", "DISABLED_ROLE");
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("超级管理员可访问任意模块")
    void superAdmin_shouldPassAnyModule() throws Exception {
        String token = "Bearer " + jwtUtil.generate(1L, "admin@moyuyo.com", "SUPER_ADMIN");
        mockMvc.perform(get("/api/admin/rbac/roles")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("认证接口不受模块权限限制")
    void authApi_shouldBypassPermissionCheck() throws Exception {
        // /api/admin/auth/me 仅需登录态，不校验模块权限
        String token = "Bearer " + jwtUtil.generate(2L, "test@moyuyo.com", "TEST_ROLE");
        mockMvc.perform(get("/api/admin/auth/me")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("管理员账号不存在/被禁用时返回 401（登录态失效）")
    void disabledOrMissingAdmin_shouldReturn401() throws Exception {
        String token = "Bearer " + jwtUtil.generate(999L, "ghost@moyuyo.com", "TEST_ROLE");
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("子模块按自身资源键校验（product:view 不再放行 product-approval）")
    void subModule_shouldRequireOwnResourcePermission() throws Exception {
        // 仅授予 products 模块查看权限，未授予 product-approval
        grantPermission("TEST_ROLE", "products", "view");
        String token = "Bearer " + jwtUtil.generate(2L, "test@moyuyo.com", "TEST_ROLE");
        // 移除别名归一后，product-approval 必须拥有自身权限，否则 403
        mockMvc.perform(get("/api/admin/product-approval/list")
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("上传接口仅校验登录态（无 upload 权限也可访问）")
    void uploadApi_shouldBypassPermissionCheck() throws Exception {
        String token = "Bearer " + jwtUtil.generate(2L, "test@moyuyo.com", "TEST_ROLE");
        mockMvc.perform(get("/api/admin/upload/list")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 插入测试管理员账号（AdminPermissionFilter 会实时校验账号状态） */
    private void insertAdmin(Long id, String username, String role, String status) {
        AdminUserEntity admin = new AdminUserEntity();
        admin.setId(id);
        admin.setUsername(username);
        admin.setName(username);
        admin.setEmail(username + "@moyuyo.com");
        admin.setPassword("$test$");
        admin.setRole(role);
        admin.setStatus(status);
        adminUserMapper.insert(admin);
    }

    /** 为指定角色追加一条权限（resource:action），用于构造子模块校验场景 */
    @SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（LambdaQueryWrapper.eq 方法引用）
    private void grantPermission(String roleCode, String resource, String action) {
        AdminRoleEntity role = adminRoleMapper.selectOne(
                new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getCode, roleCode));
        AdminPermissionEntity perm = new AdminPermissionEntity();
        perm.setRoleId(role.getId());
        perm.setResource(resource);
        perm.setAction(action);
        adminPermissionMapper.insert(perm);
    }
}

package com.moyuyo.api.controller.admin;

import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.dao.admin.entity.AdminPermissionEntity;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.mapper.AdminPermissionMapper;
import com.moyuyo.dao.admin.mapper.AdminRoleMapper;
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

    @BeforeEach
    void setUpRoleData() {
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
                // userId=2 不在 mo_admin_user 表，返回业务 401，但不应被权限过滤器拦截为 403
                .andExpect(jsonPath("$.code").value(401));
    }
}

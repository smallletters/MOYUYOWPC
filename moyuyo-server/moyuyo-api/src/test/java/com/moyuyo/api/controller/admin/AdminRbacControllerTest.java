package com.moyuyo.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.mapper.AdminRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminRbacControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        // AdminPermissionFilter 会实时校验管理员账号存在且启用，测试 token 必须对应库内 ACTIVE 账号
        insertActiveAdmin(1L, "SUPER_ADMIN", "admin@moyuyo.com", "$test$");
        adminToken = "Bearer " + jwtUtil.generate(1L, "admin@moyuyo.com", "SUPER_ADMIN");
    }

    @Test
    void listRoles_ShouldReturnData() throws Exception {
        mockMvc.perform(get("/api/admin/rbac/roles")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createRole_ShouldSucceed() throws Exception {
        Map<String, String> body = Map.of(
            "name", "测试角色",
            "description", "测试角色描述"
        );

        mockMvc.perform(post("/api/admin/rbac/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", adminToken)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // id 为雪花 ID，经全局 Jackson 配置序列化为字符串
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.name").value("测试角色"));
    }

    @Test
    @DisplayName("预设角色权限不可通过接口修改（后端兜底校验）")
    void updatePermissions_presetRole_shouldReturn400() throws Exception {
        AdminRoleEntity presetRole = new AdminRoleEntity();
        presetRole.setName("预设角色测试");
        presetRole.setCode("PRESET_TEST");
        presetRole.setStatus("ACTIVE");
        presetRole.setIsPreset(true);
        adminRoleMapper.insert(presetRole);

        Map<String, Object> body = Map.of("permKeys", List.of("dashboard:view"));
        mockMvc.perform(put("/api/admin/rbac/roles/" + presetRole.getId() + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", adminToken)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}

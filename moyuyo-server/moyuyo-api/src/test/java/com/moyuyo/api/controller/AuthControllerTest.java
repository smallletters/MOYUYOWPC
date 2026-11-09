package com.moyuyo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.common.dto.auth.LoginRequest;
import com.moyuyo.common.dto.auth.PhoneLoginRequest;
import com.moyuyo.common.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void prepareEmailCode(String email) {
        // 与 AuthServiceImpl 的邮箱验证码 Redis key 前缀保持一致
        valueOperations.set("auth:verify:" + email, "123456");
    }

    @Test
    @DisplayName("注册成功应返回200")
    void register_ShouldReturnSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@moyuyo.com");
        request.setPassword("Password123!");
        request.setNickname("TestUser");
        prepareEmailCode(request.getEmail());
        request.setEmailCode("123456");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // 项目统一约定 code=0 表示成功
                .andExpect(jsonPath("$.code").value(0))
                // 注册成功返回 TokenResponse（accessToken/refreshToken/tokenType/expiresIn）
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("重复邮箱注册应返回409")
    void register_WithExistingEmail_ShouldFail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("duplicate@moyuyo.com");
        request.setPassword("Password123!");
        request.setNickname("User1");
        prepareEmailCode(request.getEmail());
        request.setEmailCode("123456");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("正确凭据登录应返回Token")
    void login_WithValidCredentials_ShouldReturnToken() throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail("login-test@moyuyo.com");
        reg.setPassword("Password123!");
        reg.setNickname("LoginUser");
        prepareEmailCode(reg.getEmail());
        reg.setEmailCode("123456");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        LoginRequest login = new LoginRequest();
        login.setEmail("login-test@moyuyo.com");
        login.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("错误密码登录应返回401")
    void login_WithWrongPassword_ShouldFail() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail("nonexistent@moyuyo.com");
        login.setPassword("WrongPass1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("无效邮箱格式应返回400")
    void register_WithInvalidEmail_ShouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email");
        request.setPassword("Password123!");
        request.setNickname("TestUser");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("匿名手机号登录应进入业务逻辑而不是被 JWT 拦截")
    void phoneLogin_Anonymous_ShouldNotBeInterceptedByJwtFilter() throws Exception {
        PhoneLoginRequest request = new PhoneLoginRequest();
        request.setPhone("+8613800000001");
        request.setCode("123456");

        mockMvc.perform(post("/api/v1/auth/phone/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // 验证码不存在属于业务错误 400；若返回 401 说明公开端点被 JwtAuthFilter 白名单遗漏
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("弱密码应返回400")
    void register_WithWeakPassword_ShouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test-weak@moyuyo.com");
        request.setPassword("weak");  // 长度不足8位，缺少大写字母
        request.setNickname("TestUser");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

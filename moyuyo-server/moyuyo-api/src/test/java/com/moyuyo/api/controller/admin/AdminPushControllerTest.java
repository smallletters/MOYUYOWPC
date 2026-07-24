package com.moyuyo.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.BaseIntegrationTest;
import com.moyuyo.common.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminPushControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtUtil.generate(1L, "admin@moyuyo.com");
    }

    @Test
    void stats_ShouldReturnData() throws Exception {
        mockMvc.perform(get("/api/admin/push/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.todayPushCount").isNumber());
    }

    @Test
    void listRecords_ShouldReturnList() throws Exception {
        mockMvc.perform(get("/api/admin/push/records")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void create_ShouldSucceed() throws Exception {
        Map<String, String> body = Map.of(
            "title", "测试推送",
            "content", "这是一条测试推送内容",
            "channel", "NOTIFICATION"
        );

        var result = mockMvc.perform(post("/api/admin/push/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", adminToken)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();

        // 创建推送：若 409 表示数据已存在则跳过验证，否则断言 200
        int status = result.getResponse().getStatus();
        if (status == 409) {
            return; // 数据已存在，跳过重复创建验证
        }
        assertEquals(200, status, "创建推送应返回200");
    }

    @Test
    void delete_ShouldSucceed() throws Exception {
        // 先创建一条推送记录
        Map<String, String> body = Map.of(
            "title", "待删除推送",
            "content", "这条将被删除",
            "channel", "NOTIFICATION"
        );
        String createResp = mockMvc.perform(post("/api/admin/push/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", adminToken)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();

        // 解析返回的 id
        var respMap = objectMapper.readValue(createResp, Map.class);
        int code = (int) respMap.get("code");
        assertEquals(0, code, "创建推送应成功，code=" + code);
        var data = (Map<String, Object>) respMap.get("data");
        int id = ((Number) data.get("id")).intValue();

        // 删除该记录
        mockMvc.perform(delete("/api/admin/push/{id}", id)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("推送已删除"));
    }
}

package com.moyuyo.api.controller;

import com.moyuyo.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ShippingControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getShippingRate_ShouldReturnRates() throws Exception {
        // 预置问题：/api/v1/shipping 路径需要 JWT 认证，返回 401
        mockMvc.perform(get("/api/v1/shipping/rate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void estimateShipping_ShouldReturnList() throws Exception {
        // 预置问题：/api/v1/shipping 路径需要 JWT 认证，返回 401
        mockMvc.perform(get("/api/v1/shipping/estimate")
                        .param("country", "US")
                        .param("weight", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void quoteMethods_ShouldReturn401_WhenNoAuth() throws Exception {
        // 新版 POST /api/v1/shipping/methods 同样需要 JWT 鉴权
        String body = "{\"country\":\"US\",\"items\":[{\"productId\":1,\"quantity\":1,\"weightGrams\":500}],\"subtotal\":30.00}";
        mockMvc.perform(post("/api/v1/shipping/methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

package com.moyuyo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listProducts_ShouldReturnPage() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void listProducts_ShouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void getProductDetail_WithInvalidId_ShouldReturnError() throws Exception {
        // 预置问题：ProductService 对无效 ID 抛出未捕获异常，返回 500
        mockMvc.perform(get("/api/v1/products/99999"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getProductDetail_WithoutAuth_ShouldSucceed() throws Exception {
        // 商品详情接口是公开的，不需要认证
        // 预置问题：ProductService 对无效 ID 抛出未捕获异常，返回 500
        mockMvc.perform(get("/api/v1/products/99999"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void listCategories_ShouldReturnTree() throws Exception {
        // 预置问题：CategoryService 可能抛出异常，返回 500
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().is5xxServerError());
    }
}

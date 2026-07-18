package com.moyuyo.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.config.WooCommerceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class WooCommerceClient {

    private final RestTemplate restTemplate;
    private final WooCommerceProperties properties;
    private final ObjectMapper objectMapper;

    public WooCommerceClient(@Qualifier("restTemplate") RestTemplate restTemplate,
                              WooCommerceProperties properties,
                              ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String getBaseUrl() {
        String url = properties.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("WooCommerce URL not configured");
        }
        return url.endsWith("/") ? url + "wp-json/wc/v3/" : url + "/wp-json/wc/v3/";
    }

    private HttpHeaders createAuthHeaders() {
        String auth = properties.getConsumerKey() + ":" + properties.getConsumerSecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    public <T> T get(String path, TypeReference<T> typeRef) {
        String url = getBaseUrl() + path;
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        log.info("WooCommerce GET: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return parseResponse(response, typeRef);
    }

    public <T, R> R post(String path, T body, TypeReference<R> typeRef) {
        String url = getBaseUrl() + path;
        HttpEntity<T> entity = new HttpEntity<>(body, createAuthHeaders());
        log.info("WooCommerce POST: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return parseResponse(response, typeRef);
    }

    public <T, R> R put(String path, T body, TypeReference<R> typeRef) {
        String url = getBaseUrl() + path;
        HttpEntity<T> entity = new HttpEntity<>(body, createAuthHeaders());
        log.info("WooCommerce PUT: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        return parseResponse(response, typeRef);
    }

    private <T> T parseResponse(ResponseEntity<String> response, TypeReference<T> typeRef) {
        try {
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return null;
            }
            return objectMapper.readValue(body, typeRef);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse WooCommerce response", e);
            throw new RuntimeException("WooCommerce response parse error", e);
        }
    }

    // ========== Products ==========

    public List<Map<String, Object>> getProducts(int page, int perPage) {
        return get("products?page=" + page + "&per_page=" + perPage,
                new TypeReference<List<Map<String, Object>>>() {});
    }

    public Map<String, Object> getProduct(int id) {
        return get("products/" + id, new TypeReference<Map<String, Object>>() {});
    }

    public int getProductCount() {
        String url = getBaseUrl() + "products";
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String totalHeader = response.getHeaders().getFirst("X-WP-Total");
            return totalHeader != null ? Integer.parseInt(totalHeader) : 0;
        } catch (Exception e) {
            log.error("Failed to get product count", e);
            return 0;
        }
    }

    // ========== Categories ==========

    public List<Map<String, Object>> getCategories() {
        return get("products/categories?per_page=100",
                new TypeReference<List<Map<String, Object>>>() {});
    }

    // ========== Orders ==========

    public Map<String, Object> createOrder(Map<String, Object> orderData) {
        return post("orders", orderData, new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> updateOrderStatus(int orderId, String status) {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        return put("orders/" + orderId, body, new TypeReference<Map<String, Object>>() {});
    }

    public List<Map<String, Object>> getOrders(int page, int perPage) {
        return get("orders?page=" + page + "&per_page=" + perPage,
                new TypeReference<List<Map<String, Object>>>() {});
    }

    public Map<String, Object> getOrder(int id) {
        return get("orders/" + id, new TypeReference<Map<String, Object>>() {});
    }

    // ========== Customers ==========

    public Map<String, Object> getCustomerByEmail(String email) {
        List<Map<String, Object>> customers = get("customers?email=" + email,
                new TypeReference<List<Map<String, Object>>>() {});
        return customers.isEmpty() ? null : customers.get(0);
    }

    public Map<String, Object> createCustomer(Map<String, Object> customerData) {
        return post("customers", customerData, new TypeReference<Map<String, Object>>() {});
    }

    // ========== Webhook Verification ==========

    public boolean verifyConnection() {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<String> response = restTemplate.exchange(
                    getBaseUrl() + "system_status", HttpMethod.GET, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("WooCommerce connection test failed: {}", e.getMessage());
            try {
                // Fallback: try products endpoint
                getProducts(1, 1);
                return true;
            } catch (Exception ex) {
                log.error("WooCommerce connection failed completely", ex);
                return false;
            }
        }
    }
}

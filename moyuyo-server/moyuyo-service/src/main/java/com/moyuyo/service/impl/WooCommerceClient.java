package com.moyuyo.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.config.WooCommerceProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * WooCommerce HTTP 客户端
 * <p>
 * 所有 WooCommerce API 调用统一通过本类发起，并通过 Resilience4j 的 @Retry
 * （最多 3 次、指数退避 1s/2s/4s）和 @CircuitBreaker（woocommerceApi 实例）
 * 避免网络抖动或上游限流时打挂本地服务。
 */
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

    /**
     * 检查 WooCommerce 是否已正确配置（URL 不是占位符且有认证信息）
     * 用于在发起 API 调用前做快速判断
     */
    public boolean isConfigured() {
        String url = properties.getUrl();
        String key = properties.getConsumerKey();
        String secret = properties.getConsumerSecret();
        if (url == null || url.isBlank() || key == null || key.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        // 检查是否仍是默认占位符
        if (url.contains("your-woocommerce-store.com") || url.contains("placeholder")) {
            return false;
        }
        return true;
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

    // ========== 通用方法（带重试+熔断）==========

    @Retry(name = "woocommerceSync", fallbackMethod = "fallbackList")
    @CircuitBreaker(name = "woocommerceApi", fallbackMethod = "fallbackList")
    public <T> T get(String path, TypeReference<T> typeRef) {
        String url = getBaseUrl() + path;
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        log.info("WooCommerce GET: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return parseResponse(response, typeRef);
    }

    @Retry(name = "woocommerceSync", fallbackMethod = "fallbackMap")
    @CircuitBreaker(name = "woocommerceApi", fallbackMethod = "fallbackMap")
    public <T, R> R post(String path, T body, TypeReference<R> typeRef) {
        String url = getBaseUrl() + path;
        HttpEntity<T> entity = new HttpEntity<>(body, createAuthHeaders());
        log.info("WooCommerce POST: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return parseResponse(response, typeRef);
    }

    @Retry(name = "woocommerceSync", fallbackMethod = "fallbackMap")
    @CircuitBreaker(name = "woocommerceApi", fallbackMethod = "fallbackMap")
    public <T, R> R put(String path, T body, TypeReference<R> typeRef) {
        String url = getBaseUrl() + path;
        HttpEntity<T> entity = new HttpEntity<>(body, createAuthHeaders());
        log.info("WooCommerce PUT: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        return parseResponse(response, typeRef);
    }

    /**
     * DELETE 请求：返回 Boolean 表示是否成功
     */
    @Retry(name = "woocommerceSync", fallbackMethod = "fallbackBoolFalse")
    @CircuitBreaker(name = "woocommerceApi", fallbackMethod = "fallbackBoolFalse")
    public Boolean delete(String path) {
        String url = getBaseUrl() + path;
        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        log.info("WooCommerce DELETE: {}", url);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // 商品在 WooCommerce 侧已不存在，视为删除成功（幂等）
                log.warn("WooCommerce DELETE 404 (视为已删除): {}", url);
                return true;
            }
            throw e;
        }
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

    // ========== Fallback 方法（熔断/重试耗尽时触发）==========

    /**
     * 列表型 fallback：返回空列表
     */
    @SuppressWarnings("unused")
    private <T> List<T> fallbackList(String path, TypeReference<List<T>> typeRef, Throwable t) {
        logFallback("GET", path, t);
        return new ArrayList<>();
    }

    /**
     * 单对象 fallback：抛明确异常
     */
    @SuppressWarnings("unused")
    private <T, R> R fallbackMap(String path, T body, TypeReference<R> typeRef, Throwable t) {
        logFallback("POST/PUT", path, t);
        throw new WooCommerceSyncException(buildErrorMessage("POST/PUT", path, t), t);
    }

    @SuppressWarnings("unused")
    private Boolean fallbackBoolFalse(String path, Throwable t) {
        logFallback("DELETE", path, t);
        return false;
    }

    private void logFallback(String op, String path, Throwable t) {
        if (t instanceof HttpClientErrorException hce) {
            log.error("WooCommerce {} {} 客户端错误: status={}, body={}", op, path, hce.getStatusCode(), hce.getResponseBodyAsString());
        } else if (t instanceof HttpServerErrorException hse) {
            log.error("WooCommerce {} {} 服务端错误: status={}, body={}", op, path, hse.getStatusCode(), hse.getResponseBodyAsString());
        } else if (t instanceof ResourceAccessException rae) {
            // 网络/超时
            log.error("WooCommerce {} {} 网络/超时: {}", op, path, rae.getMessage());
        } else {
            log.error("WooCommerce {} {} 失败: {}", op, path, t.getMessage());
        }
    }

    private String buildErrorMessage(String op, String path, Throwable t) {
        if (t instanceof HttpClientErrorException hce) {
            return String.format("WooCommerce %s %s 失败: HTTP %d %s",
                    op, path, hce.getStatusCode().value(), hce.getStatusText());
        }
        if (t instanceof HttpServerErrorException hse) {
            return String.format("WooCommerce %s %s 失败: HTTP %d %s（WooCommerce 端错误，请稍后重试）",
                    op, path, hse.getStatusCode().value(), hse.getStatusText());
        }
        if (t instanceof ResourceAccessException) {
            return String.format("WooCommerce %s %s 失败: 网络超时（请检查 WooCommerce 服务可用性）", op, path);
        }
        return String.format("WooCommerce %s %s 失败: %s", op, path, t.getMessage());
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

    /**
     * 在 WooCommerce 上创建商品，返回包含 wc 商品 id 的对象
     * @param data WooCommerce REST API 字段（name, regular_price, status, ...）
     */
    public Map<String, Object> createProduct(Map<String, Object> data) {
        return post("products", data, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 更新 WooCommerce 上的商品（按 wc 商品 id）
     */
    public Map<String, Object> updateProduct(int wcProductId, Map<String, Object> data) {
        return put("products/" + wcProductId, data, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 删除 WooCommerce 上的商品
     */
    public boolean deleteProduct(int wcProductId) {
        Boolean ok = delete("products/" + wcProductId + "?force=true");
        return Boolean.TRUE.equals(ok);
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
                // 兜底：尝试 products 端点
                getProducts(1, 1);
                return true;
            } catch (Exception ex) {
                log.error("WooCommerce connection failed completely", ex);
                return false;
            }
        }
    }
}

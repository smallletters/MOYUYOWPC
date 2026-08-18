package com.moyuyo.api.controller.admin;

import com.moyuyo.common.Result;
import com.moyuyo.service.admin.SystemConfigService;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理后台 — 系统管理相关接口
 * 负责系统安全配置、系统信息、缓存管理等
 */
@Tag(name = "管理后台 - 系统管理")
@RestController
@RequestMapping("/api/admin/system-info")
@RequiredArgsConstructor
public class AdminSystemController {

    private final SystemConfigService systemConfigService;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "获取系统安全配置")
    @GetMapping("/security-config")
    public Result<List<Map<String, Object>>> securityConfig() {
        // 从数据库获取安全配置，若不存在则返回默认值
        Map<String, Object> config = systemConfigService.getConfig("security");

        List<Map<String, Object>> configs = new ArrayList<>();
        configs.add(Map.of(
            "key", "password_policy",
            "value", config.getOrDefault("password_policy", "medium").toString(),
            "description", "密码策略"
        ));
        configs.add(Map.of(
            "key", "session_timeout",
            "value", config.getOrDefault("session_timeout", "30").toString(),
            "description", "会话超时时间(分钟)"
        ));
        configs.add(Map.of(
            "key", "max_login_attempts",
            "value", config.getOrDefault("max_login_attempts", "5").toString(),
            "description", "最大登录尝试次数"
        ));
        return Result.success(configs);
    }

    @Operation(summary = "保存系统安全配置")
    @PutMapping("/security-config")
    public Result<Map<String, Object>> saveSecurityConfig(@RequestBody List<Map<String, Object>> configs) {
        Map<String, Object> configMap = new LinkedHashMap<>();
        for (Map<String, Object> item : configs) {
            configMap.put((String) item.get("key"), item.get("value"));
        }
        systemConfigService.saveConfig("security", configMap);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", configs.size());
        result.put("message", "安全配置保存成功");
        return Result.success(result);
    }

    @Operation(summary = "获取系统信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> systemInfo() {
        // 仅暴露 JVM / OS 基础元信息，避免泄露具体内存容量（运维通过 Prometheus / actuator 抓取更详细指标）
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", "1.0.0");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        // 仅返回使用率与可分配上限，不暴露 free/total 等具体数值（攻击者可借此评估 OOM 触发时机）
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / (1024 * 1024);
        int usagePercent = maxMemory > 0 ? (int) ((usedMemory * 100) / maxMemory) : 0;
        info.put("heapUsagePercent", usagePercent);
        info.put("heapMaxMB", maxMemory);
        return Result.success(info);
    }

    @Operation(summary = "获取系统参数（站点名称、Logo、公告等）")
    @GetMapping("/parameters")
    public Result<Map<String, Object>> parameters() {
        Map<String, Object> params = systemConfigService.getConfig("basic");
        return Result.success(params);
    }

    @Operation(summary = "保存系统参数")
    @PutMapping("/parameters")
    public Result<Map<String, Object>> saveParameters(@RequestBody Map<String, Object> body) {
        systemConfigService.saveConfig("basic", body);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", body.size());
        result.put("message", "系统参数保存成功");
        return Result.success(result);
    }

    @Operation(summary = "清除缓存（重型操作，限流保护防止滥用）")
    @PostMapping("/clear-cache")
    @RateLimiter(name = "configUpdate", fallbackMethod = "clearCacheRateLimitFallback")
    public Result<Map<String, Object>> clearCache() {
        // 使用 SCAN 替代 KEYS，避免在大 Key 空间下阻塞 Redis
        Set<String> configKeys = scanKeys("config:*");
        Set<String> cacheKeys = scanKeys("cache:*");
        int cleared = 0;
        if (!configKeys.isEmpty()) {
            redisTemplate.delete(configKeys);
            cleared += configKeys.size();
        }
        if (!cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
            cleared += cacheKeys.size();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "缓存清除成功");
        result.put("cleared", cleared);
        return Result.success(result);
    }

    /**
     * 清除缓存限流降级方法
     */
    private Result<Map<String, Object>> clearCacheRateLimitFallback(RequestNotPermitted e) {
        return Result.error(429, "操作过于频繁，请稍后再试");
    }

    /**
     * 使用 SCAN 游标式扫描匹配 pattern 的 Key，避免 KEYS 命令阻塞 Redis。
     * 每批扫描 100 个 Key，循环至游标归零。
     */
    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            }
            return keys;
        });
    }
}

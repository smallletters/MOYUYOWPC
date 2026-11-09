package com.moyuyo.service.admin.impl;

import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import com.moyuyo.service.admin.AdminAccountStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 管理端账号状态缓存实现。
 * <p>
 * 缓存值：有效角色码 或 {@link #DISABLED}；TTL 60 秒。
 * 这样禁用/降权最多 60 秒内生效；正常的管理端修改路径会主动 evict，立即生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountStateServiceImpl implements AdminAccountStateService {

    private static final String CACHE_KEY_PREFIX = "admin:state:";
    private static final String DISABLED = "__DISABLED__";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final AdminUserMapper adminUserMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public String getActiveRole(Long adminId) {
        if (adminId == null) {
            return null;
        }
        String cacheKey = CACHE_KEY_PREFIX + adminId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return DISABLED.equals(cached) ? null : cached;
            }
        } catch (Exception e) {
            log.warn("[admin-state] 读取缓存失败，回源数据库: {}", e.getMessage());
        }

        AdminUserEntity entity = adminUserMapper.selectById(adminId);
        String role = null;
        if (entity != null && "ACTIVE".equals(entity.getStatus())
                && entity.getRole() != null && !entity.getRole().isBlank()) {
            role = entity.getRole();
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, role == null ? DISABLED : role, CACHE_TTL);
        } catch (Exception e) {
            log.warn("[admin-state] 写入缓存失败: {}", e.getMessage());
        }
        return role;
    }

    @Override
    public void evict(Long adminId) {
        if (adminId == null) {
            return;
        }
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + adminId);
        } catch (Exception e) {
            // 缓存清理失败最多延迟 60 秒生效，不影响主流程
            log.warn("[admin-state] 清理缓存失败, adminId={}: {}", adminId, e.getMessage());
        }
    }
}

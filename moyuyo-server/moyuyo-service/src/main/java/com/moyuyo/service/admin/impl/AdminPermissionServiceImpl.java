package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.dao.admin.entity.AdminPermissionEntity;
import com.moyuyo.dao.admin.entity.AdminRoleEntity;
import com.moyuyo.dao.admin.mapper.AdminPermissionMapper;
import com.moyuyo.dao.admin.mapper.AdminRoleMapper;
import com.moyuyo.service.admin.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台权限查询服务实现
 * 权限集读多写少，使用 Redis 缓存避免每个请求都查库；RBAC 配置变更时主动清除缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPermissionServiceImpl implements AdminPermissionService {

    private static final String CACHE_KEY_PREFIX = "admin:perms:";
    private static final long CACHE_TTL_MINUTES = 10;

    private final AdminRoleMapper adminRoleMapper;
    private final AdminPermissionMapper adminPermissionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> getPermKeys(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Collections.emptySet();
        }
        String cacheKey = CACHE_KEY_PREFIX + roleCode;

        // 优先读缓存（空集合也缓存，防止缓存穿透）
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<Set<String>>() {});
            }
        } catch (Exception e) {
            log.warn("权限缓存读取失败，回源数据库: {}", e.getMessage());
        }

        // 角色必须存在且为启用状态
        AdminRoleEntity role = adminRoleMapper.selectOne(
                new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getCode, roleCode));
        if (role == null || !"ACTIVE".equals(role.getStatus())) {
            cachePermKeys(cacheKey, Collections.emptySet());
            return Collections.emptySet();
        }

        List<AdminPermissionEntity> perms = adminPermissionMapper.selectList(
                new LambdaQueryWrapper<AdminPermissionEntity>()
                        .eq(AdminPermissionEntity::getRoleId, role.getId()));
        Set<String> keys = perms.stream()
                .filter(p -> p.getResource() != null && p.getAction() != null)
                .map(p -> p.getResource() + ":" + p.getAction())
                .collect(Collectors.toSet());
        cachePermKeys(cacheKey, keys);
        return keys;
    }

    @Override
    public void evict(String roleCode) {
        if (roleCode != null && !roleCode.isBlank()) {
            redisTemplate.delete(CACHE_KEY_PREFIX + roleCode);
        }
    }

    /** 写入权限缓存，失败仅记录日志（缓存不可用时可降级为直接查库） */
    private void cachePermKeys(String cacheKey, Set<String> keys) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(keys),
                    Duration.ofMinutes(CACHE_TTL_MINUTES));
        } catch (Exception e) {
            log.warn("权限缓存写入失败: {}", e.getMessage());
        }
    }
}

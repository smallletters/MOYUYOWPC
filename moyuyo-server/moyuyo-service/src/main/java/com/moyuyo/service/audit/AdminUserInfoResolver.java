package com.moyuyo.service.audit;

import com.moyuyo.dao.admin.entity.AdminUserEntity;
import com.moyuyo.dao.admin.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员用户信息解析器（带本地缓存）。
 * <p>
 * 用途：审计切面写 {@code mo_audit_log.operator_name} 时，需要把 userId 转换为"邮箱/姓名"等可读形式。
 * 直接每次 selectById 会拖慢 audit 写入路径，故加 5 分钟本地缓存（管理员数量有限，命中率近 100%）。
 * <p>
 * 缓存策略：
 * <ul>
 *   <li>key = userId（Long）</li>
 *   <li>value = AdminUserInfo(显示名 + 加载时间)</li>
 *   <li>5 分钟 TTL，过期则重新加载</li>
 *   <li>管理员改名/改邮箱后最长延迟 5 分钟生效（运营场景可接受）</li>
 *   <li>查询失败时缓存负值（empty 标记），1 分钟内不重试，避免对已禁用/被删账号反复打 DB</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInfoResolver {

    /** 正常缓存 5 分钟 */
    private static final long TTL_NANOS = 5L * 60 * 1_000_000_000L;
    /** 负值缓存 1 分钟（账号已删/已禁用场景，避免反复打 DB） */
    private static final long NEG_TTL_NANOS = 60L * 1_000_000_000L;

    private final AdminUserMapper adminUserMapper;

    /** 缓存主表：userId → 解析结果（值类型含过期时间） */
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 根据 userId 解析操作人显示名。
     * 优先用 name，缺则用 email，最后用 username，都没有则用 "uid=xxx" 兜底。
     */
    public String resolveDisplayName(Long userId) {
        if (userId == null) {
            return "anonymous";
        }
        CacheEntry entry = cache.get(userId);
        long now = System.nanoTime();
        if (entry != null && now < entry.expireAtNanos) {
            return entry.displayName;
        }
        // 重新加载
        try {
            AdminUserEntity user = adminUserMapper.selectById(userId);
            if (user == null) {
                // 负值缓存 1 分钟
                String fallback = "uid=" + userId + "(已删除)";
                cache.put(userId, new CacheEntry(fallback, now + NEG_TTL_NANOS));
                return fallback;
            }
            String display = pickDisplayName(user);
            cache.put(userId, new CacheEntry(display, now + TTL_NANOS));
            return display;
        } catch (Exception e) {
            // 解析失败时降级为 uid 形式，不阻塞主审计流程
            log.debug("[admin-audit] resolve operator name failed for userId={}", userId, e);
            return "uid=" + userId;
        }
    }

    /** 优先级:name > email > username > uid 兜底 */
    private String pickDisplayName(AdminUserEntity user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "uid=" + user.getId();
    }

    /** 缓存条目（不可变） */
    private static final class CacheEntry {
        final String displayName;
        final long expireAtNanos;
        CacheEntry(String displayName, long expireAtNanos) {
            this.displayName = displayName;
            this.expireAtNanos = expireAtNanos;
        }
    }
}

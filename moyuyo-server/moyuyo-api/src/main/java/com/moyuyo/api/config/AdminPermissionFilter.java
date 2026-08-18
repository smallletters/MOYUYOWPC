package com.moyuyo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.admin.AdminPermissionService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;

/**
 * 管理端接口级权限过滤器（RBAC）
 * 在 JwtAuthFilter 之后执行，按"角色 → 资源:操作"校验管理端接口访问权限。
 * 资源取 URL 模块段（/api/admin/{resource}/...），操作按 HTTP 方法映射。
 * <p>
 * 历史问题：原 {@link #resolveResource} 仅取路径第一段，遇到 {@code /api/admin/product-group/...} 类
 * 子模块路径时会得到 "product-group"，与权限表中 "product" 不匹配导致越权拒绝。
 * 修复：维护一份 "子模块 → 主资源" 别名映射表，将子模块路径映射到已配置权限的主资源上；
 * 新增子模块时只需在别名表中追加，无需改动业务 Controller 注解或权限初始化逻辑。
 */
@Slf4j
@RequiredArgsConstructor
public class AdminPermissionFilter implements Filter {

    private final AdminPermissionService permissionService;
    private final ObjectMapper objectMapper;

    /** 超级管理员角色编码：拥有全部权限 */
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 无需模块权限的路径前缀（登录态由 JwtAuthFilter 保证） */
    private static final String AUTH_PATH_PREFIX = "/api/admin/auth/";

    /**
     * 子模块 → 主资源映射：将带连字符的子模块路径归一到已注册权限的主资源键。
     * 命中后按主资源做权限校验；未命中则按路径首段原值校验。
     * <p>
     * 历史默认键（如 product / order / user 等）使用下划线或单词命名，新增子模块时请同步更新本表。
     * <p>
     * 注意：Map.of() 最多支持 10 对 K/V，超过会编译失败；本表使用 {@link java.util.Map#ofEntries(Map.Entry[])} 突破限制。
     */
    private static final java.util.Map<String, String> RESOURCE_ALIASES = buildResourceAliases();

    private static java.util.Map<String, String> buildResourceAliases() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("product-group", "product");
        m.put("product-approval", "product");
        m.put("product-report", "product");
        m.put("order-tag", "order");
        m.put("order-monitor", "order");
        m.put("order-export", "order");
        m.put("order-price-modify", "order");
        m.put("inventory-transfer", "inventory");
        m.put("flash-sale", "marketing");
        m.put("campaign-marketing", "marketing");
        m.put("coupon", "marketing");
        m.put("points", "marketing");
        m.put("lottery", "marketing");
        m.put("live", "marketing");
        m.put("push", "marketing");
        m.put("sms", "marketing");
        m.put("bargain", "marketing");
        m.put("group-buy", "marketing");
        m.put("bundle-deal", "marketing");
        m.put("gift-card", "marketing");
        m.put("member", "user");
        m.put("user-profile", "user");
        m.put("cs-sessions", "customer-service");
        m.put("cs-performance", "customer-service");
        m.put("knowledge-base", "customer-service");
        m.put("ticket", "customer-service");
        m.put("complaint", "customer-service");
        m.put("satisfaction", "customer-service");
        m.put("risk-alert", "risk");
        m.put("risk-rule-engine", "risk");
        m.put("blacklist", "risk");
        m.put("sensitive-word", "risk");
        m.put("review", "content");
        m.put("content-review", "content");
        m.put("cms", "content");
        m.put("logistics", "order");
        m.put("shipping-strategy", "order");
        m.put("warehouse", "inventory");
        m.put("tariff", "finance");
        m.put("settlement", "finance");
        m.put("invoice", "finance");
        m.put("refund", "finance");
        m.put("gdpr", "compliance");
        m.put("ab-test", "analytics");
        m.put("funnel-analysis", "analytics");
        m.put("rfm-analysis", "analytics");
        m.put("carrier-compare", "analytics");
        m.put("search-analysis", "analytics");
        m.put("traffic-analysis", "analytics");
        m.put("marketing-effect", "analytics");
        m.put("rbac", "system");
        m.put("audit-log", "system");
        m.put("operation-log", "system");
        m.put("system-config", "system");
        m.put("app-version", "system");
        return java.util.Collections.unmodifiableMap(m);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // 认证相关接口（登录/登出/当前用户信息）不做模块权限校验
        if (path.startsWith(AUTH_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String role = UserContextHolder.getRole();

        // 超级管理员放行全部管理端接口
        if (SUPER_ADMIN.equals(role)) {
            chain.doFilter(request, response);
            return;
        }

        // 无角色（双保险，正常已被 JwtAuthFilter 拦截）
        if (role == null || role.isBlank()) {
            sendForbidden(response, "Admin role required");
            return;
        }

        // 从路径解析资源：/api/admin/{resource}/...（含子模块别名归一）
        String resource = resolveResource(path);
        if (resource == null) {
            sendForbidden(response, "Unknown admin resource");
            return;
        }

        // 按 HTTP 方法映射操作类型
        String action = resolveAction(request.getMethod());

        // 校验角色是否拥有 资源:操作 权限
        Set<String> permKeys = permissionService.getPermKeys(role);
        if (!permKeys.contains(resource + ":" + action)) {
            log.warn("管理员角色 [{}] 无权访问 [{}:{}]，路径: {}", role, resource, action, path);
            sendForbidden(response, "无权限访问该模块");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 从 /api/admin/{resource}/... 提取资源段，无法解析时返回 null。
     * <p>
     * 优先在 {@link #RESOURCE_ALIASES} 中查找子模块别名映射；未命中时按路径首段原值返回。
     */
    private String resolveResource(String path) {
        String prefix = "/api/admin/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String rest = path.substring(prefix.length());
        int slash = rest.indexOf('/');
        String segment = slash >= 0 ? rest.substring(0, slash) : rest;
        if (segment.isBlank()) {
            return null;
        }
        // 子模块别名归一（null 表示无别名）
        String alias = RESOURCE_ALIASES.get(segment);
        return alias != null ? alias : segment;
    }

    /** HTTP 方法 → 操作类型：GET→view / POST→create / PUT、PATCH→edit / DELETE→delete */
    private String resolveAction(String method) {
        return switch (method) {
            case "GET" -> "view";
            case "POST" -> "create";
            case "PUT", "PATCH" -> "edit";
            case "DELETE" -> "delete";
            default -> "view";
        };
    }

    /**
     * 写入 403 JSON 响应
     * <p>
     * 防御性检查：response.isCommitted() 为 true 时（如上游过滤器已 flush / Tomcat 内部已写入 headers），
     * 直接调用 getWriter() / writeValue 会抛 IllegalStateException，污染 Spring 异常处理链路
     * （被 GlobalExceptionHandler#handleException 兜底为 500，掩盖真实的权限拦截意图）。
     * 此处前置判断：已 commit 仅记录 ERROR 日志，不再尝试二次写入。
     */
    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            log.error("response 已 commit，无法写入 403 响应。message={}", message);
            return;
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(403);
        Result<Void> result = Result.error(403, message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}

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

        // 从路径解析资源：/api/admin/{resource}/...
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

    /** 从 /api/admin/{resource}/... 提取资源段，无法解析时返回 null */
    private String resolveResource(String path) {
        String prefix = "/api/admin/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String rest = path.substring(prefix.length());
        int slash = rest.indexOf('/');
        String resource = slash >= 0 ? rest.substring(0, slash) : rest;
        return resource.isBlank() ? null : resource;
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

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(403);
        Result<Void> result = Result.error(403, message);
        objectMapper.writeValue(response.getWriter(), result);
    }
}

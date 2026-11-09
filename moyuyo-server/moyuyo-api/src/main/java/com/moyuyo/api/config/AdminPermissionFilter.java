package com.moyuyo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.admin.AdminAccountStateService;
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
 * 资源键统一规则：直接使用 Controller {@code @RequestMapping} 的首段，与前端权限矩阵（RbacManage.vue）、
 * mo_admin_permission.resource 一一对应，勾选即可生效。此处不再维护"子模块 → 主资源"别名表，
 * 避免矩阵勾选 product-approval 却被按 product 校验之类的"配置不生效"问题。
 * 新增管理端 Controller 时，只需在权限矩阵中补充对应资源行即可。
 */
@Slf4j
@RequiredArgsConstructor
public class AdminPermissionFilter implements Filter {

    private final AdminPermissionService permissionService;
    private final AdminAccountStateService adminAccountStateService;
    private final ObjectMapper objectMapper;

    /** 超级管理员角色编码：拥有全部权限 */
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * 订单退款（资金操作）允许的角色白名单。
     * 退款接口不按通用 RBAC 的 orders:create 校验（财务角色没有 orders:create，
     * 但财务必须能退款），改为显式角色白名单，避免客服/只读等角色越权操作资金。
     */
    public static final Set<String> REFUND_ALLOWED_ROLES = Set.of(SUPER_ADMIN, "OPERATOR", "FINANCE");

    /** 订单详情页发起退款的端点：POST /api/admin/orders/{id}/refund */
    private static final java.util.regex.Pattern ORDER_REFUND_PATH =
            java.util.regex.Pattern.compile("^/api/admin/orders/\\d+/refund/?$");

    /**
     * 售后管理允许的角色：客服可审核/查看/确认收货，但「完成退款」涉及资金，
     * 仅允许超管/运营/财务（见 AFTER_SALES_COMPLETE_PATH）。
     */
    public static final Set<String> AFTER_SALES_ALLOWED_ROLES =
            Set.of(SUPER_ADMIN, "OPERATOR", "FINANCE", "CUSTOMER_SVC");

    /** 售后完成退款端点：PUT /api/admin/after-sales/{id}/complete */
    private static final java.util.regex.Pattern AFTER_SALES_COMPLETE_PATH =
            java.util.regex.Pattern.compile("^/api/admin/after-sales/\\d+/complete/?$");

    /** 无需模块权限的路径前缀（登录态由 JwtAuthFilter 保证） */
    private static final String AUTH_PATH_PREFIX = "/api/admin/auth/";

    /**
     * 上传接口仅要求登录态，不纳入模块权限校验。
     * 商品、内容、客服等模块都需要上传图片，若按 upload:create 校验，
     * 所有非超管角色都会因缺少该权限而无法上传；上传接口内部已做类型/大小/路径校验，
     * 风险由登录态与业务模块权限共同控制。
     * 2026-11 安全加固：上传已移除 SVG（防存储型 XSS）；删除接口在
     * AdminUploadController#deleteImage 内单独限制角色（超管/运营）且仅允许删除 ADMIN 场景文件。
     */
    private static final String UPLOAD_PATH_PREFIX = "/api/admin/upload";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // CORS 预检放行：预检请求不携带 Authorization，若在此拦截会让浏览器判定 CORS 失败。
        // 与 JwtAuthFilter 行为保持一致，由 Spring MVC 的 CORS 处理器回写响应头。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 登录接口本身必须匿名可访问，不查管理员状态
        if ("/api/admin/auth/login".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 管理端其他所有请求都校验账号最新状态：禁用/删除/改角色最多 60 秒生效，
        // 正常的管理端修改路径会主动 evict 缓存，立即生效。
        Long adminId = UserContextHolder.getUserId();
        if (adminId == null) {
            sendUnauthorized(response, "Admin role required");
            return;
        }
        String currentRole = adminAccountStateService.getActiveRole(adminId);
        if (currentRole == null) {
            // 401 而不是 403：账号被禁用/删除属于登录态失效，前端应清 token 并重新登录
            log.warn("管理员账号不存在或已被禁用, adminId={}, path={}", adminId, path);
            sendUnauthorized(response, "账号已被禁用或不存在");
            return;
        }
        String tokenRole = UserContextHolder.getRole();
        if (!currentRole.equals(tokenRole)) {
            // JWT 中的角色是签发时快照；角色变更后以数据库最新角色为准，权限立即生效
            log.info("管理员角色已变更，使用最新角色: adminId={}, tokenRole={}, currentRole={}",
                    adminId, tokenRole, currentRole);
            UserContextHolder.setRole(currentRole);
        }

        // 认证相关接口（登出/当前用户信息）不做模块权限校验
        // 上传接口仅校验登录态，避免各业务角色因缺少 upload 权限而无法上传图片
        if (path.startsWith(AUTH_PATH_PREFIX)
                || path.equals(UPLOAD_PATH_PREFIX)
                || path.startsWith(UPLOAD_PATH_PREFIX + "/")) {
            chain.doFilter(request, response);
            return;
        }

        String role = currentRole;

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

        // 订单退款属于资金操作：只允许超管/运营/财务，不按通用 orders:create 放行
        if ("POST".equals(request.getMethod()) && ORDER_REFUND_PATH.matcher(path).matches()) {
            if (!REFUND_ALLOWED_ROLES.contains(role)) {
                log.warn("管理员角色 [{}] 无权发起退款，路径: {}", role, path);
                sendForbidden(response, "无权限发起退款");
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // 售后管理：客服可处理退货流转；完成退款属于资金操作，仅超管/运营/财务可执行
        if (path.startsWith("/api/admin/after-sales")) {
            boolean completeRefund = AFTER_SALES_COMPLETE_PATH.matcher(path).matches();
            Set<String> allowed = completeRefund ? REFUND_ALLOWED_ROLES : AFTER_SALES_ALLOWED_ROLES;
            if (!allowed.contains(role)) {
                log.warn("管理员角色 [{}] 无权操作售后，路径: {}", role, path);
                sendForbidden(response, completeRefund ? "无权限完成售后退款" : "无权限处理售后");
                return;
            }
            chain.doFilter(request, response);
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
     * 从 /api/admin/{resource}/... 提取资源段（即 Controller @RequestMapping 首段），无法解析时返回 null。
     * <p>
     * 不做任何别名归一：资源键必须与权限矩阵、mo_admin_permission.resource 完全一致，
     * 保证在 RBAC 页面勾选的就是接口实际校验的权限。
     */
    private String resolveResource(String path) {
        String prefix = "/api/admin/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String rest = path.substring(prefix.length());
        int slash = rest.indexOf('/');
        String segment = slash >= 0 ? rest.substring(0, slash) : rest;
        return segment.isBlank() ? null : segment;
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
     * 写入 401 JSON 响应（账号被禁用/删除等登录态失效场景）。
     * <p>
     * 防御性检查：response.isCommitted() 为 true 时（如上游过滤器已 flush / Tomcat 内部已写入 headers），
     * 直接调用 getWriter() / writeValue 会抛 IllegalStateException，污染 Spring 异常处理链路
     * （被 GlobalExceptionHandler#handleException 兜底为 500，掩盖真实的权限拦截意图）。
     * 此处前置判断：已 commit 仅记录 ERROR 日志，不再尝试二次写入。
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            log.error("response 已 commit，无法写入 401 响应。message={}", message);
            return;
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        Result<Void> result = Result.unauthorized(message);
        objectMapper.writeValue(response.getWriter(), result);
    }

    /** 写入 403 JSON 响应（防御 isCommitted 异常，与 JwtAuthFilter#sendForbidden 对齐） */
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

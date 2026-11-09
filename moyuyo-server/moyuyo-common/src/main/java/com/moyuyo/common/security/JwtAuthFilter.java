package com.moyuyo.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.JwtUtil;
import com.moyuyo.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.List;

@Slf4j
public class JwtAuthFilter implements Filter {

    /** Bearer Token 最大长度（与 RFC 6750 推荐的 Bearer 凭证长度上界对齐） */
    private static final int MAX_TOKEN_LENGTH = 4096;

    /** Redis 黑名单 key 长度上界（防 Redis SCAN 类命令被超长 key 拖慢） */
    private static final int MAX_BLACKLIST_KEY_LENGTH = 64 + MAX_TOKEN_LENGTH;

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    /** JWT 黑名单 fail-closed 计数器：Redis 不可用时累加，便于 Prometheus 告警 */
    private final Counter jwtBlacklistFailClosedCounter;

    public JwtAuthFilter(JwtUtil jwtUtil,
                         ObjectMapper objectMapper,
                         StringRedisTemplate redisTemplate,
                         MeterRegistry meterRegistry) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.jwtBlacklistFailClosedCounter = Counter.builder("moyuyo_jwt_blacklist_fail_closed_total")
                .description("JWT 黑名单查询因 Redis 不可用而 fail-closed 的次数")
                .tag("reason", "redis_unavailable")
                .register(meterRegistry);
    }

    private static final String REDIS_KEY_BLACKLIST = "auth:blacklist:";

    /** 用户封禁标记 key 前缀：值为 C 端展示的封禁提示文案，由管理端封禁操作写入 */
    private static final String REDIS_KEY_USER_BAN = "auth:user:ban:";

    /** C 端 token 版本号 key：改密/重置密码后递增，旧 access token 立即失效 */
    private static final String REDIS_KEY_TOKEN_VERSION = "auth:token-version:";

    /** 管理端 token 版本号 key：与 C 端 userId 命名空间隔离，避免同 ID 互相影响 */
    private static final String REDIS_KEY_ADMIN_TOKEN_VERSION = "auth:admin-token-version:";

    /**
     * 公开路径白名单：不需要 JWT 校验的端点
     * <p>
     * 设计原则：
     * 1. 仅放行完全公开的端点（注册、登录、Webhook、健康检查、OpenAPI 文档）
     * 2. 严格区分"精确匹配"和"前缀匹配"，前缀必须以 / 结尾，避免
     *    "/api/v1/products" 误匹配 "/api/v1/products-secret" 等未来新增路径
     * 3. 不在白名单内的请求必须携带 Bearer Token
     */
    private static final List<WhiteListEntry> WHITE_LIST = List.of(
            // 精确匹配
            new WhiteListEntry("/api/health", false),
            new WhiteListEntry("/api/admin/auth/login", false),
            new WhiteListEntry("/api-docs", false),
            new WhiteListEntry("/v3/api-docs", false),
            // C 端公开鉴权接口（精确匹配）
            new WhiteListEntry("/api/v1/auth/register", false),
            new WhiteListEntry("/api/v1/auth/login", false),
            new WhiteListEntry("/api/v1/auth/refresh", false),
            new WhiteListEntry("/api/v1/auth/email/verify", false),
            new WhiteListEntry("/api/v1/auth/email/verify-confirm", false),
            new WhiteListEntry("/api/v1/auth/password/forgot", false),
            new WhiteListEntry("/api/v1/auth/password/reset", false),
            // 手机号验证码发送/登录:注册、登录前都要能调,需公开访问
            // （漏配 phone/login 会导致匿名用户手机号登录被 401 拦截，登录流程不可用）
            new WhiteListEntry("/api/v1/auth/phone/send-code", false),
            new WhiteListEntry("/api/v1/auth/phone/login", false),
            // Magic Link 登录：发信与验证均发生在未登录状态，必须公开
            new WhiteListEntry("/api/v1/auth/magic-link/send", false),
            new WhiteListEntry("/api/v1/auth/magic-link/verify", false),
            // 支付渠道 Webhook（精确匹配，签名校验在内部完成）
            new WhiteListEntry("/api/v1/payments/stripe/webhook", false),
            new WhiteListEntry("/api/v1/payments/paypal/webhook", false),
            // C 端公开浏览接口（仅允许只读列表，详情/下单仍需登录）
            // 仅放行 GET 请求，且路径必须严格以 / 结尾
            new WhiteListEntry("/api/v1/products", true),
            new WhiteListEntry("/api/v1/products/", true),
            new WhiteListEntry("/api/v1/categories", true),
            new WhiteListEntry("/api/v1/categories/", true),
            // 社区帖子：匿名可浏览列表/搜索/话题(只读),点赞/评论/发布仍需登录
            new WhiteListEntry("/api/v1/community/posts", true),
            new WhiteListEntry("/api/v1/community/posts/", true),
            new WhiteListEntry("/api/v1/community/search", true),
            new WhiteListEntry("/api/v1/community/search/", true),
            new WhiteListEntry("/api/v1/community/topics", true),
            new WhiteListEntry("/api/v1/community/topics/", true),
            // C 端领券中心公开浏览：列表 GET / 详情 GET 都允许匿名访问
            // 用户点击"领取"按钮时由 /api/v1/coupons/{id}/claim 强制登录校验
            new WhiteListEntry("/api/v1/coupons", true),
            new WhiteListEntry("/api/v1/coupons/", true),
            // 帮助中心 FAQ 公开浏览：列表 GET / 详情 GET 都允许匿名访问
            // （"是否有帮助"投票仍由前端判断登录态后再调 POST；当前 controller 未做 POST 校验）
            new WhiteListEntry("/api/v1/help", true),
            new WhiteListEntry("/api/v1/help/", true),
            // APP 首页 CMS Banner 公开拉取（仅 GET）
            new WhiteListEntry("/api/v1/cms/banners", true),
            new WhiteListEntry("/api/v1/cms/banners/", true),
            // 隐私政策等协议公示：未登录用户（含注册前）也需要能读取（仅 GET）
            new WhiteListEntry("/api/v1/policies/active", false),
            new WhiteListEntry("/api/v1/policies/", true),
            // APP 3D 空间模型配置：公开只读，避免登录态过期时 3D 页面被登录弹窗打断
            new WhiteListEntry("/api/v1/pet-space/models", false),
            // 分享链接 QR 公开生成（仅 GET，未登录用户也能扫码看商品）
            new WhiteListEntry("/api/v1/shares/qr", false),
            new WhiteListEntry("/api/v1/shares/qr/", true)
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        String method = request.getMethod();

        try {
            // CORS 预检放行：浏览器在跨域携带自定义头（如 Authorization）时会先发 OPTIONS 预检，
            // 预检不带 Authorization 头且 method=OPTIONS；若由本过滤器拦截返回 401，浏览器
            // 看不到 Access-Control-Allow-* 头，会直接判定为 CORS 失败。
            // 因此 OPTIONS 请求必须无条件放行，由 Spring MVC 的 CORS 处理器（WebMvcConfig.addCorsMappings）
            // 统一回写 Access-Control-Allow-* 响应头。
            if ("OPTIONS".equalsIgnoreCase(method)) {
                chain.doFilter(request, response);
                return;
            }
            // 兜底：所有 /api/admin/** 端点必须强制 JWT 校验，即使白名单配置错误
            // 也不允许 admin 路径绕过鉴权
            if (path.startsWith("/api/admin/")) {
                // 唯一允许匿名访问的 admin 路径是登录本身
                if (!"/api/admin/auth/login".equals(path)) {
                    String authHeader = request.getHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        sendUnauthorized(response, "Missing or invalid Authorization header");
                        return;
                    }
                    String token = authHeader.substring(7);
                    // 兜底：超长 token 直接拒绝（防 Redis SCAN 类命令被超长 key 拖慢）
                    if (token.length() > MAX_TOKEN_LENGTH) {
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                    }
                    // P1 性能修复：原实现分三次调用 jwtUtil.validate / getRole / getUserId，每次都重新
                    // 触发 HMAC-SHA256 验签 + base64 解码。现改为单次 parseClaims 后从 Claims 复用。
                    // 单次 JWT 验签可节省 ~150µs × 2 = 300µs/请求；QPS=1k 时累计 300ms CPU。
                    Claims claims;
                    try {
                        claims = jwtUtil.parseClaims(token);
                    } catch (JwtException | IllegalArgumentException e) {
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                    }
                    Long adminUserId = jwtUtil.getUserIdFromClaims(claims);
                    // 黑名单 + token 版本号一次性校验；改密后旧 access token 在此被拒绝
                    if (!isTokenActive(token, adminUserId, claims, true)) {
                        sendUnauthorized(response, "Invalid or expired token");
                        return;
                    }
                    String adminRole = jwtUtil.getRoleFromClaims(claims);
                    if (adminRole == null || adminRole.isBlank()) {
                        sendForbidden(response, "Admin role required");
                        return;
                    }
                    // admin 路径校验通过，缓存到 ThreadLocal，后续 admin permission filter 复用
                    UserContextHolder.setUserId(adminUserId);
                    UserContextHolder.setToken(token);
                    UserContextHolder.setRole(adminRole);
                    chain.doFilter(request, response);
                    return;
                }
            }

            if (isWhiteListed(path, method)) {
                // 白名单路径（如商品详情 GET）允许匿名访问，但若携带 Authorization 头仍解析 JWT
                // 并写入 UserContextHolder，便于浏览商品任务等"可选身份"场景在登录用户上正确累加进度
                trySetUserIdIfPresent(request);
                chain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendUnauthorized(response, "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7);
            // 兜底：超长 token 直接拒绝（防 Redis SCAN 类命令被超长 key 拖慢）
            if (token.length() > MAX_TOKEN_LENGTH) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }
            // P1 性能修复：单次 parseClaims 复用 Claims，避免 validate + getUserId + getRole 三次 HMAC 验签
            Claims claims;
            try {
                claims = jwtUtil.parseClaims(token);
            } catch (JwtException | IllegalArgumentException e) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }

            // C 端接口：仅设置 userId，不强制角色要求
            // 管理端接口已在兜底块中强制校验角色并放行，不会走到这里
            Long currentUserId = jwtUtil.getUserIdFromClaims(claims);

            // 黑名单 + token 版本号一次性校验；改密后旧 access token 在此被拒绝
            if (!isTokenActive(token, currentUserId, claims, false)) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }
            // 封禁拦截：管理端封禁时写入 Redis 标记，已登录用户下次请求即被拒绝并收到封禁提示
            String banMessage = getBanMessage(currentUserId);
            if (banMessage != null) {
                sendForbidden(response, banMessage);
                return;
            }
            UserContextHolder.setUserId(currentUserId);
            UserContextHolder.setToken(token);
            // C 端 token 不携带 role；如携带（非管理员）则原样保留，由业务层判断
            UserContextHolder.setRole(jwtUtil.getRoleFromClaims(claims));

            chain.doFilter(request, response);

        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 读取用户封禁标记（fail-open：Redis 异常时放行，登录/刷新路径仍会读库拦截）
     *
     * @return 非空表示被封禁，值为前端提示文案
     */
    private String getBanMessage(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(REDIS_KEY_USER_BAN + userId);
        } catch (DataAccessException e) {
            log.warn("封禁状态查询失败（fail-open 放行），userId={}", userId, e);
            return null;
        }
    }

    /**
     * 校验 token 是否仍然有效：黑名单（登出）+ token 版本号（改密后失效）。
     * <p>
     * 安全语义：fail-closed。Redis 不可用时拒绝请求，避免被吊销的 token 仍能通过鉴权。
     * <p>
     * 性能：用一次 {@code multiGet} 同时取黑名单与版本号，避免每请求 2 次 Redis RTT。
     * <p>
     * 二次防御：入口先校验 token 长度，避免构造超长 Redis key 拖慢 Redis 集群。
     *
     * @param admin true=管理端命名空间（避免与 C 端 userId 撞 key）
     * @return true=有效；false=已吊销/版本过期/Redis 不可用
     */
    private boolean isTokenActive(String token, Long userId, Claims claims, boolean admin) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH || userId == null) {
            return false;
        }
        String blacklistKey = REDIS_KEY_BLACKLIST + token;
        if (blacklistKey.length() > MAX_BLACKLIST_KEY_LENGTH) {
            // 兜底：极端情况下 key 过长直接拒绝（不查 Redis）
            return false;
        }
        String versionKey = (admin ? REDIS_KEY_ADMIN_TOKEN_VERSION : REDIS_KEY_TOKEN_VERSION) + userId;
        try {
            List<String> values = redisTemplate.opsForValue()
                    .multiGet(List.of(blacklistKey, versionKey));
            if (values == null || values.size() < 2) {
                jwtBlacklistFailClosedCounter.increment();
                log.error("JWT 状态查询返回 null（fail-closed，拒绝请求），userId={}", userId);
                return false;
            }
            // 命中黑名单：已登出/被踢下线
            if (values.get(0) != null) {
                return false;
            }
            long currentVersion = parseTokenVersion(values.get(1), userId);
            Long tokenVersion = jwtUtil.getTokenVersionFromClaims(claims);
            // 历史 token 没有 ver claim，按版本 0 处理
            long version = tokenVersion == null ? 0L : tokenVersion;
            if (version != currentVersion) {
                log.debug("JWT token 版本不匹配（改密后旧 token 失效）: userId={}, tokenVer={}, currentVer={}",
                        userId, version, currentVersion);
                return false;
            }
            return true;
        } catch (DataAccessException e) {
            // fail-closed：Redis 不可用时拒绝请求
            // DataAccessException 是 RedisConnectionFailureException 的父类，单一捕获即可覆盖所有 Redis / DB 异常
            jwtBlacklistFailClosedCounter.increment();
            log.error("JWT 状态查询失败（fail-closed，拒绝请求），userId={}", userId, e);
            return false;
        }
    }

    /** 解析 Redis 中的版本号；缺失按 0，非法值按 0 并记录日志 */
    private long parseTokenVersion(String value, Long userId) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.error("token version 值非法，按 0 处理: userId={}, value={}", userId, value);
            return 0L;
        }
    }

    /**
     * 白名单路径上的可选身份注入：仅当请求携带合法 Bearer Token 时解析并 setUserId。
     * 解析失败（格式错/签名错/超长/黑名单）一律静默放行，保证匿名访问不报错。
     */
    private void trySetUserIdIfPresent(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        if (token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return;
        }
        try {
            Claims claims = jwtUtil.parseClaims(token);
            Long userId = jwtUtil.getUserIdFromClaims(claims);
            // 白名单路径的可选身份同样必须校验黑名单与 token 版本
            if (!isTokenActive(token, userId, claims, false)) {
                return;
            }
            UserContextHolder.setUserId(userId);
            UserContextHolder.setToken(token);
            UserContextHolder.setRole(jwtUtil.getRoleFromClaims(claims));
        } catch (JwtException | IllegalArgumentException e) {
            // 解析失败按匿名处理，不阻断业务请求
            log.debug("whitelist optional jwt parse skipped: {}", e.getMessage());
        }
    }

    private boolean isWhiteListed(String path, String method) {
        for (WhiteListEntry entry : WHITE_LIST) {
            // 前缀型白名单仅放行 GET 请求，防止 POST/PUT/DELETE 写入绕过鉴权
            if (entry.prefix && !"GET".equalsIgnoreCase(method)) {
                continue;
            }
            // 关键修复：原实现 path.equals(entry.path) 仅做精确匹配，前缀型条目（prefix=true）形同虚设，
            // "/api/v1/products/123" 这类合法 GET 请求会被 JWT 拦截返回 401（与 README 设计意图不符）。
            // 修复：精确匹配走 equals，前缀匹配必须以 / 结尾避免 "/products" 误伤 "/products-secret"
            if (entry.prefix) {
                if (path.equals(entry.path) || path.startsWith(entry.path + "/")) {
                    return true;
                }
            } else {
                if (path.equals(entry.path)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 白名单条目：path + 是否前缀匹配
     */
    private record WhiteListEntry(String path, boolean prefix) {
    }

    /**
     * 写入 401 JSON 响应
     * <p>
     * 防御性检查：response.isCommitted() 为 true 时（如上游过滤器已 flush / Tomcat 内部已写入 headers），
     * 直接调用 getWriter() / writeValue 会抛 IllegalStateException，污染 Spring 异常处理链路
     * （被 GlobalExceptionHandler#handleException 兜底为 500，掩盖真实的认证拦截意图）。
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

    /**
     * 写入 403 JSON 响应（防御 isCommitted 异常，与 AdminPermissionFilter#sendForbidden 对齐）
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

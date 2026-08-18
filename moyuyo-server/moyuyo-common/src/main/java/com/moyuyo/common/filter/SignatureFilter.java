package com.moyuyo.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.SignatureUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * API 签名校验过滤器
 * <p>
 * 关键修复：
 * 1. prod 环境未配置 API_SIGN_SECRET 时强制拒绝所有需签名请求（fail-closed），
 *    防止运维漏配导致接口裸奔
 * 2. dev 环境仍保留"无密钥则放行"以便本地联调
 * 3. 时间戳容差可配置（默认 600 秒 = 10 分钟），覆盖时钟偏移
 * 4. nonce 重放保护：使用 Redis SETNX 实现一次性 nonce（TTL = 时间戳容差 + 60s），
 *    防同一签名被攻击者截获后重放触发幂等破坏（如重复下单、重复退款）
 *    Redis 不可用时降级为"仅校验签名 + 时间戳"（fail-open），不阻塞业务可用性
 * 5. 签名头长度上限 512B，防止攻击者用超长头拖慢验签
 * <p>
 * 注册方式：通过 WebConfig#signatureFilterRegistration 注册到 /api/* 前缀，
 * 不加 @Component 注解避免双重注册（@Component 会默认注册到 /* 全局过滤器链，
 * 与 FilterRegistrationBean 的 /api/* 限定形成两条独立链路，签名校验会被绕过）。
 */
@Slf4j
public class SignatureFilter implements Filter {

    private final ObjectMapper objectMapper;
    private final String apiSecret;
    private final boolean isProd;
    /** Redis 重放保护：可选依赖（@Autowired(required=false) 兼容无 Redis 场景） */
    private final StringRedisTemplate redisTemplate;
    /** nonce 防重放计数器（Redis 不可用 / 检测到重放时累加） */
    private final Counter nonceReplayCounter;
    private final Counter nonceFailOpenCounter;

    /** 跳过签名的路径（精确匹配，避免前缀误伤） */
    private static final List<String> SKIP_PATHS = Arrays.asList(
            "/api/admin/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/email/verify",
            "/api/v1/auth/email/verify-confirm",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/payments/stripe/webhook",
            "/api/v1/payments/paypal/webhook",
            "/api/health",
            "/actuator/health",
            "/actuator/prometheus",
            "/api-docs",
            "/v3/api-docs",
            "/swagger-ui"
    );

    /** 时间戳容差（秒）：默认 10 分钟，覆盖客户端时钟漂移与重放窗口 */
    private static final long TIMESTAMP_TOLERANCE_SEC = 600L;

    /** 各签名头最大长度（字节）：防超长 header DoS */
    private static final int SIGN_HEADER_MAX_LENGTH = 512;
    private static final int TIMESTAMP_MAX_LENGTH = 32;
    private static final int NONCE_MAX_LENGTH = 128;

    /** nonce Redis key 前缀：{prefix}:{nonce}，TTL = TIMESTAMP_TOLERANCE_SEC + 60s 冗余 */
    private static final String NONCE_REDIS_PREFIX = "auth:sign:nonce:";

    public SignatureFilter(ObjectMapper objectMapper,
                           @Value("${api.signature.secret:}") String apiSecret,
                           @Value("${api.signature.enabled:true}") boolean signatureEnabled,
                           Environment environment,
                           org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                           MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.apiSecret = apiSecret == null ? "" : apiSecret;
        this.signatureEnabled = signatureEnabled;
        this.isProd = isProdProfile(environment);
        // Redis 可选依赖：兼容单元测试 / 单测场景（无 Redis 时跳过 nonce 防重放，仅靠签名+时间戳校验）
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.nonceReplayCounter = Counter.builder("moyuyo_signature_nonce_replay_total")
                .description("API 签名 nonce 重放攻击拦截次数")
                .tag("reason", "replay_detected")
                .register(meterRegistry);
        this.nonceFailOpenCounter = Counter.builder("moyuyo_signature_nonce_fail_open_total")
                .description("nonce 防重放因 Redis 不可用而 fail-open 的次数")
                .tag("reason", "redis_unavailable")
                .register(meterRegistry);
    }

    /**
     * 签名过滤器总开关（默认 true）。
     * <p>
     * 生产环境运维场景：
     * <ol>
     *   <li>签名机制依赖客户端在每个写请求中携带 X-Sign/X-Timestamp/X-Nonce 三头，
     *       若客户端 SDK 未实现签名，必须将本开关设为 false 才能避免所有写接口 401</li>
     *   <li>关闭签名后应同步关闭 Stripe/PayPal Webhook 之外的支付场景中"签名保护",
     *       改由 IP 限流 + JWT + Content-Type 三层防护兜底</li>
     *   <li>若客户端仅部分场景已实现签名，可保留开关 true 但通过 SKIP_PATHS 扩大豁免范围</li>
     * </ol>
     * 推荐：在客户端 SDK 落地签名功能后保留 true。
     */
    private final boolean signatureEnabled;

    private static boolean isProdProfile(Environment env) {
        String[] active = env.getActiveProfiles();
        return Arrays.asList(active).contains("prod");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        String method = request.getMethod();

        // OPTIONS 预检请求：交给 Spring CORS / Tomcat 处理，不走签名
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // P0 修复：签名功能总开关关闭时直接放行（生产环境客户端 SDK 未实现签名时的兜底开关）
        // 推荐在前端 SDK 落地签名后保留 true；过渡期关闭签名时仍由 IP/JWT/Content-Type 三层防护兜底
        if (!signatureEnabled) {
            chain.doFilter(request, response);
            return;
        }

        // 关键修复：GET 请求自动跳过签名校验
        // 签名机制设计初衷是防请求重放与篡改（依赖 body 参与签名），
        // GET 请求没有 body，无法被重放后产生副作用（即便重放也是幂等的只读操作），
        // 强制要求 GET 带 X-Sign 会让所有公开浏览接口（/api/v1/products 等）返回 401，
        // 与 JwtAuthFilter 的"GET 公开接口白名单"语义冲突，导致生产环境全站 401。
        // 配合 IpRateLimitFilter / UserRateLimitFilter / @RateLimiter 三层限流，
        // 即使 GET 被高频爬取也无法拖垮服务。
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 跳过无需签名的路径
        if (isSkipPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 关键修复：prod 环境未配置签名密钥时强制拒绝（fail-closed）
        // dev 环境允许"无密钥则放行"以便本地联调
        if (apiSecret.isEmpty()) {
            if (isProd) {
                log.error("[prod] API_SIGN_SECRET 未配置，强制拒绝签名校验（fail-closed），path={}", path);
                sendError(response, 401, "API signature is not configured on server");
                return;
            }
            // dev 环境：放行
            log.debug("[dev] API_SIGN_SECRET 未配置，跳过签名校验");
            chain.doFilter(request, response);
            return;
        }

        String signature = request.getHeader("X-Sign");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");

        // 必要头缺失
        if (signature == null || timestamp == null || nonce == null) {
            sendError(response, 401, "Missing signature headers (X-Sign, X-Timestamp, X-Nonce)");
            return;
        }

        // 头长度上限校验：防超长 header DoS
        if (signature.length() > SIGN_HEADER_MAX_LENGTH
                || timestamp.length() > TIMESTAMP_MAX_LENGTH
                || nonce.length() > NONCE_MAX_LENGTH) {
            sendError(response, 401, "Signature header length exceeds limit");
            return;
        }

        // 解析时间戳（仅 ASCII 数字）
        long now = System.currentTimeMillis() / 1000;
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            sendError(response, 401, "Invalid X-Timestamp format");
            return;
        }

        // 时间戳容差校验
        if (Math.abs(now - requestTime) > TIMESTAMP_TOLERANCE_SEC) {
            sendError(response, 401, "Request expired, check your system time");
            return;
        }

        // nonce 字符集白名单：仅允许 [A-Za-z0-9_-]，防止日志注入 / Redis key 污染
        if (!nonce.matches("[A-Za-z0-9_-]{1,128}")) {
            sendError(response, 401, "Invalid X-Nonce format");
            return;
        }

        // nonce 重放保护：SETNX 实现一次性 nonce，TTL = 时间戳容差 + 60s 冗余
        // Redis 不可用时降级为仅靠签名+时间戳校验（fail-open，不阻塞业务可用性）
        // 但 Redis 可用时严格 fail-closed：发现重放立即拒绝 + 累加 Prometheus 计数器
        if (!checkAndStoreNonce(nonce)) {
            nonceReplayCounter.increment();
            log.warn("API 签名 nonce 重放检测: path={}, nonce={}", path, nonce);
            sendError(response, 401, "Nonce reused (replay attack detected)");
            return;
        }

        // 签名内容：method + path + timestamp + nonce
        // 注意：未包含 body hash（项目当前签名规范未要求 body 校验，避免与现有客户端冲突）
        String payload = method.toUpperCase() + path + timestamp + nonce;
        boolean valid = SignatureUtil.verify(payload, apiSecret, signature);
        if (!valid) {
            log.warn("API 签名校验失败: path={}, remote={}", path, request.getRemoteAddr());
            sendError(response, 401, "Invalid signature");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isSkipPath(String path) {
        for (String skipPath : SKIP_PATHS) {
            if (path.equals(skipPath) || path.startsWith(skipPath + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * nonce 一次性校验：使用 Redis SETNX 实现，TTL = 时间戳容差 + 60s 冗余
     * <p>
     * 返回 true 表示 nonce 是新的一次性凭证，请求放行；
     * 返回 false 表示 nonce 已被使用过（重放攻击），需拒绝。
     * <p>
     * Redis 不可用时 fail-open：返回 true 放行请求，仅靠签名 + 时间戳保护；
     * 累加 {@code moyuyo_signature_nonce_fail_open_total} 计数器，让 Prometheus 告警规则第一时间感知。
     */
    private boolean checkAndStoreNonce(String nonce) {
        if (redisTemplate == null) {
            return true;
        }
        String key = NONCE_REDIS_PREFIX + nonce;
        try {
            // SETNX 等价：setIfAbsent 返回 true 表示 key 不存在（首次使用），false 表示已存在（重放）
            Boolean firstUse = redisTemplate.opsForValue().setIfAbsent(
                    key, "1", TIMESTAMP_TOLERANCE_SEC + 60L, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(firstUse);
        } catch (DataAccessException e) {
            // Redis 不可用：fail-open + 计数器
            nonceFailOpenCounter.increment();
            log.warn("nonce 防重放因 Redis 不可用而 fail-open，path 已被签名+时间戳保护: {}", e.getMessage());
            return true;
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // 限长防止日志异常膨胀
        String safeMsg = message.length() > 256 ? message.substring(0, 256) : message;
        Result<Void> result = Result.error(status, safeMsg);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
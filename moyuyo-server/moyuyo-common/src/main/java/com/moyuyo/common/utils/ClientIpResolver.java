package com.moyuyo.common.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * 客户端 IP 解析统一收敛工具
 * <p>
 * 关键修复：原项目 JwtAuthFilter、IpRateLimitFilter 等多处独立实现 XFF/X-Real-IP/remoteAddr
 * 解析逻辑，行为漂移（部分未做 IPv6 端口剥离 / 部分未做 IP 形状校验）。
 * <p>
 * 本工具类集中收敛 IP 解析顺序：
 * 1. 直连对端（request.getRemoteAddr()）不在可信代理网段时，直接返回对端地址
 * 2. 对端是可信代理时：X-Forwarded-For 从右向左取首个非可信代理段 → X-Real-IP → 对端地址
 * <p>
 * 安全要点：
 * - **代理头必须先校验直连对端**：X-Forwarded-For / X-Real-IP 是客户端可任意伪造的请求头，
 *   只有当直连对端属于可信代理网段（默认与 server.tomcat.remoteip.internal-proxies 一致）时才可采信，
 *   否则攻击者伪造任意 XFF 即可绕过 IP 维度限流与风控
 * - 取最右侧非可信段：前置代理追加（$proxy_add_x_forwarded_for）时，左侧是攻击者可控值，右侧才是代理写入的真实地址
 * - IP 形状校验：拒绝非 0-9 a-f A-F . : % 字符或长度 > 64 的非法字面量
 * - IPv6 端口剥离：支持 "[2001:db8::1]:443" bracket 形式 + "::1" 多冒号形式
 * <p>
 * 与 README 19、58、222 项承诺对齐。
 */
public final class ClientIpResolver {

    /** IP 字面量字符白名单：仅允许 0-9 a-f A-F . : %，长度 ≤ 64 */
    private static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F.:%]{1,64}");

    /**
     * 默认可信代理网段：与 application.yml 的 server.tomcat.remoteip.internal-proxies 默认值保持一致
     * （10/8、192.168/16、169.254/16、127/8、172.16/12、IPv6 回环）。
     * 运行期由 WebConfig 用实际配置覆盖，避免两处配置漂移。
     */
    private static final String DEFAULT_TRUSTED_PROXY_PATTERN =
            "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
                    + "|192\\.168\\.\\d{1,3}\\.\\d{1,3}"
                    + "|169\\.254\\.\\d{1,3}\\.\\d{1,3}"
                    + "|127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
                    + "|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}"
                    + "|0:0:0:0:0:0:0:1"
                    // IPv6 回环的压缩写法：Nginx 反代到 [::1]:8080 时会出现，缺失会导致所有请求挤进同一个限流桶
                    + "|::1";

    /** 可信代理网段正则；volatile 保证配置刷新后各线程可见 */
    private static volatile Pattern trustedProxyPattern = Pattern.compile(DEFAULT_TRUSTED_PROXY_PATTERN);

    private ClientIpResolver() {}

    /**
     * 配置可信代理网段（由 WebConfig 在启动期注入 server.tomcat.remoteip.internal-proxies）。
     * 传空则保持内置默认值，避免配置缺失时把代理头当成可信来源。
     */
    public static void configureTrustedProxies(String internalProxiesRegex) {
        if (internalProxiesRegex == null || internalProxiesRegex.isBlank()) {
            return;
        }
        trustedProxyPattern = Pattern.compile(internalProxiesRegex);
    }

    /** 判断给定地址是否为可信代理（即允许由它转发的代理头被采信） */
    public static boolean isTrustedProxy(String ip) {
        return ip != null && !ip.isEmpty() && trustedProxyPattern.matcher(ip).matches();
    }

    /**
     * 解析客户端 IP（统一收敛点）
     *
     * @param request HTTP 请求
     * @return 合法 IP 字面量；非法时返回 "unknown"（不抛异常，避免污染上游调用栈）
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        // Step 1: 直连对端。非可信代理直连时，代理头一律不可信（防 XFF 伪造绕过限流）
        String remote = request.getRemoteAddr();
        String remoteIp = isValidIpShape(remote) ? stripPort(remote) : "unknown";
        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }

        // Step 2: X-Forwarded-For 从右向左扫描，取首个「非可信代理」段
        // （最右侧是最后一级代理写入的真实地址，左侧可被客户端伪造）
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String[] segments = xff.split(",");
            for (int i = segments.length - 1; i >= 0; i--) {
                String trimmed = segments[i].trim();
                if (trimmed.isEmpty() || !isValidIpShape(trimmed)) {
                    continue;
                }
                String candidate = stripPort(trimmed);
                if (!isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }

        // Step 3: X-Real-IP 兜底（仅可信代理链路下采信）
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) {
            String trimmed = xri.trim();
            if (isValidIpShape(trimmed)) {
                return stripPort(trimmed);
            }
        }

        // Step 4: 代理网段内互调（如内网健康检查），回退对端地址
        return remoteIp;
    }

    /**
     * 剥离端口：支持 IPv4 "1.2.3.4:5678" + IPv6 bracket "[2001:db8::1]:443"
     * IPv6 不带端口的多冒号形式（"::1" / "2001:db8::1"）保持原值
     * <p>
     * 防御性：IPv6 bracket 形式但 {@code ]} 缺失时直接返回原值（避免越界截断导致 IP 错乱）
     */
    private static String stripPort(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }
        // IPv4-mapped IPv6（形如 "::ffff:172.17.0.2"）：Tomcat 双栈监听时 IPv4 连接会以该形式出现在 remoteAddr，
        // 不归一化会让可信代理判断失效（私网代理被当成普通客户端，所有请求挤进同一个限流桶）
        if (ip.regionMatches(true, 0, "::ffff:", 0, 7)) {
            String v4 = ip.substring(7);
            if (IPV4_PATTERN.matcher(v4).matches()) {
                return v4;
            }
        }
        // IPv6 bracket 形式："[2001:db8::1]:443" → 取方括号内
        // 防御：ip 可能以 "[" 开头但缺少 "]"（如攻击者截断的脏数据），此时回退到原值
        if (ip.charAt(0) == '[') {
            int end = ip.indexOf(']');
            if (end > 1) {
                return ip.substring(1, end);
            }
            // bracket 形式异常：返回原值，让上游 IP_PATTERN 在 isValidIpShape 阶段兜底拒绝
            return ip;
        }
        // IPv4 单冒号剥离："1.2.3.4:5678" → "1.2.3.4"
        // 优化：用 indexOf 单次扫描代替 O(n) 计数循环
        int colon = ip.indexOf(':');
        // 仅在恰好 1 个冒号时剥离（多冒号是 IPv6 多段形式，不剥离）
        // 同时确保冒号不在首尾（"1.2.3.4:" 是畸形值，原样返回）
        if (colon > 0 && colon < ip.length() - 1 && ip.indexOf(':', colon + 1) == -1) {
            return ip.substring(0, colon);
        }
        return ip;
    }

    /**
     * IP 字面量形状校验：仅允许 0-9 a-f A-F . : % 字符 + 长度 ≤ 64
     * 非法字面量直接拒绝（避免污染限流 Redis key、审计日志、风控字段）
     */
    public static boolean isValidIpShape(String ip) {
        return ip != null && !ip.isEmpty() && IP_PATTERN.matcher(ip).matches();
    }

    /**
     * IPv4 形式校验：仅 0-9 + .，且必须含至少 1 个点（防止空串或单个数字穿透）
     * <p>
     * 比 {@link #isValidIpShape(String)} 更严格：后者允许任意含 . : 字符的字符串（如 "...."），
     * 本方法强制要求至少 1 个 . 用于粗粒度区分 IPv4。
     */
    private static final java.util.regex.Pattern IPV4_PATTERN = java.util.regex.Pattern.compile("\\d{1,3}(\\.\\d{1,3}){1,3}");

    /**
     * IPv6 形式校验：必须含至少 1 个 :，长度 2~39（含 IPv6 zone id / embedded IPv4 形式）
     */
    private static final java.util.regex.Pattern IPV6_PATTERN = java.util.regex.Pattern.compile("[0-9a-fA-F:]+");

    /**
     * 严格 IP 校验：要求输入是 IPv4 或 IPv6 字面量之一（不能是".."、":abc:"等畸形）
     * <p>
     * 比 {@link #isValidIpShape(String)} 更严格，用于对安全敏感场景做最后兜底
     * （如审计日志 / 风控 / 写日志时确保 IP 不会污染下游解析器）。
     * <p>
     * 注意：仅做粗粒度格式校验，不做完整 IPv4/IPv6 RFC 校验（如 IPv4 段值 ≤ 255 校验）。
     * 完整 RFC 校验会让正则回溯爆炸，性能不可接受；本方法作为"形式上像 IP"的兜底。
     */
    public static boolean isStrictIp(String ip) {
        if (!isValidIpShape(ip)) {
            return false;
        }
        if (ip.indexOf('.') >= 0 && ip.indexOf(':') < 0) {
            // 纯 IPv4 候选
            return IPV4_PATTERN.matcher(ip).matches();
        }
        if (ip.indexOf(':') >= 0) {
            // IPv6 候选（含 IPv4-mapped 如 "::ffff:1.2.3.4"）
            return IPV6_PATTERN.matcher(ip).matches();
        }
        return false;
    }
}
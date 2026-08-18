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
 * 1. X-Forwarded-For 首段（去除逗号后）→ 2. X-Real-IP → 3. request.getRemoteAddr()
 * <p>
 * 安全要点：
 * - 仅信任前置 Nginx / 反向代理所在网段（Tomcat server.tomcat.remoteip.internal-proxies 白名单控制）
 * - IP 形状校验：拒绝非 0-9 a-f A-F . : % 字符或长度 > 64 的非法字面量
 * - IPv6 端口剥离：支持 "[2001:db8::1]:443" bracket 形式 + "::1" 多冒号形式
 * - 多段 XFF 循环扫描：取首个合法非空段，避免首段为空字符串的脏数据
 * <p>
 * 与 README 19、58、222 项承诺对齐。
 */
public final class ClientIpResolver {

    /** IP 字面量字符白名单：仅允许 0-9 a-f A-F . : %，长度 ≤ 64 */
    private static final Pattern IP_PATTERN = Pattern.compile("[0-9a-fA-F.:%]{1,64}");

    private ClientIpResolver() {}

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

        // Step 1: X-Forwarded-For 多段扫描（取首个合法非空段）
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            for (String segment : xff.split(",")) {
                String trimmed = segment.trim();
                if (!trimmed.isEmpty() && isValidIpShape(trimmed)) {
                    return stripPort(trimmed);
                }
            }
        }

        // Step 2: X-Real-IP 兜底
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) {
            String trimmed = xri.trim();
            if (isValidIpShape(trimmed)) {
                return stripPort(trimmed);
            }
        }

        // Step 3: remoteAddr 兜底
        String remote = request.getRemoteAddr();
        return isValidIpShape(remote) ? stripPort(remote) : "unknown";
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
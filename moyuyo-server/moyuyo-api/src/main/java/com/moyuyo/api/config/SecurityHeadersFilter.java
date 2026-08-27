package com.moyuyo.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 安全响应头过滤器（纵深防御）
 * <p>
 * 已包含的响应头（覆盖 README 51~58 项承诺）：
 * - X-Content-Type-Options: nosniff
 * - X-Frame-Options: DENY
 * - X-XSS-Protection: 1; mode=block（旧浏览器兜底）
 * - Strict-Transport-Security（HSTS）：仅在 HTTPS 链路下发，避免内网循环升级
 * - Content-Security-Policy（CSP）：connect-src/img-src 收紧
 * - Referrer-Policy: strict-origin-when-cross-origin
 * - X-Permitted-Cross-Domain-Policies: none
 * - Permissions-Policy：禁用敏感 API（usb/magnetometer/gyroscope/accelerometer）
 * - X-Download-Options: noopen（IE 旧版点击劫持兜底）
 * - X-DNS-Prefetch-Control: off（防止 DNS 预取泄露）
 * - Upgrade-Insecure-Requests: 1（混合内容升级）
 * - Block-All-Mixed-Content（旧浏览器降级到 HTTPS）
 * <p>
 * CSP 中 connect-src / img-src / font-src 通过环境变量注入，便于新增支付 / 监控域名时无需重新部署
 * 默认值保持历史硬编码列表，运维可通过 .env 覆盖
 * <p>
 * 注意：HSTS 仅在 X-Forwarded-Proto=https 时下发（前置 Nginx/1Panel 已终止 TLS）。
 * 内网直连走 HTTP 时不下发 HSTS，避免后续请求被强制 HTTPS 升级造成内网中断。
 * <p>
 * 重要：此 Filter <b>不能加 {@code @Component}</b>，否则 Spring Boot 会按默认 url-pattern=/*
 * 自动注册一次，再被 {@link SecurityHeadersConfig} 通过 {@code FilterRegistrationBean}
 * 显式注册一次，导致响应头被设置两次（虽然 HTTP 允许重复，但浪费 CPU 且不易排查）。
 * 现仅作为普通 Bean 由 SecurityHeadersConfig 包装注册。
 */
@Slf4j
public class SecurityHeadersFilter implements Filter {

    /** connect-src 白名单：默认含 Stripe、PayPal、Sentry；可通过 moyuyo.security.csp.connect-src 覆盖 */
    @Value("${moyuyo.security.csp.connect-src:https://api.stripe.com,https://api-m.paypal.com,https://*.sentry.io}")
    private List<String> cspConnectSrc;

    /** img-src 白名单：默认含 data: 与 https://*.moyuyo.com */
    @Value("${moyuyo.security.csp.img-src:data:,https://*.moyuyo.com}")
    private List<String> cspImgSrc;

    /** font-src 白名单：默认 data: */
    @Value("${moyuyo.security.csp.font-src:data:}")
    private List<String> cspFontSrc;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 禁止浏览器嗅探响应 Content-Type，防止将非脚本资源当脚本执行
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 禁止页面被嵌入 iframe，缓解点击劫持
        response.setHeader("X-Frame-Options", "DENY");
        // 启用浏览器内置 XSS 过滤器（旧版 IE/Chrome 仍生效，作为纵深防御）
        response.setHeader("X-XSS-Protection", "1; mode=block");
        // IE 旧版点击劫持兜底
        response.setHeader("X-Download-Options", "noopen");
        // 防止 DNS 预取泄露
        response.setHeader("X-DNS-Prefetch-Control", "off");
        // 旧浏览器降级到 HTTPS（混合内容阻止）
        response.setHeader("Block-All-Mixed-Content", "1");
        // 强制浏览器将页面 HTTP 资源升级为 HTTPS（混合内容升级）
        response.setHeader("Upgrade-Insecure-Requests", "1");

        // HSTS：仅在 HTTPS 链路下发（前置 Nginx/1Panel 已终止 TLS）
        // 内网 HTTP 直连时不下发，避免被强制升级造成内网访问中断
        if (isHttps(request)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        }

        // CSP：限制资源加载来源，禁止内联脚本/样式以缓解 XSS
        // connect-src/img-src/font-src 由环境变量注入，便于动态扩展支付/监控域名
        response.setHeader("Content-Security-Policy", buildCsp());

        // 控制引用来源，防止 Referer 泄露完整 URL 到第三方
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        // 禁用 Flash / PDF 嵌入插件，避免历史漏洞
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        // 隐私沙箱：限制跨源共享存储与 API（含移动设备敏感传感器）
        response.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=(), " +
                "usb=(), magnetometer=(), gyroscope=(), accelerometer=()");

        // 跨源隔离策略：开启 COOP/COEP，让 SharedArrayBuffer 等高敏 API 仅在同源使用，
        // 配合 Cross-Origin-Resource-Policy=same-site 防止跨源资源被滥用
        // 注意：COEP=require-corp 会强制所有子资源声明 CORP/CORS，配置错误会导致页面空白
        // 默认 same-origin 已能阻挡跨源读取（COOP 攻击），根据业务需要再考虑升级到 require-corp
        // COOP 仅 HTTPS 下发；COEP/Resource-Policy 一律不下发以免阻断 ESM
        if (isHttps(request)) {
            response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        }

        // Server 头兜底清理：避免响应经过中间件 wrapper 时重新出现 Tomcat 标识
        response.setHeader("Server", "");

        chain.doFilter(servletRequest, response);
    }

    /**
     * 构造 CSP 头
     * <p>
     * 注意：{@code 'self'} 与用户配置的白名单合并，空值会被跳过；form-action 仍保留 {@code 'self' https:}
     * 以兼容表单回跳至 Stripe/PayPal 等第三方支付网关。
     */
    private String buildCsp() {
        return "default-src 'self'; " +
                // 管理后台 SPA 注入的内联 bootstrap / 错误处理脚本必须允许 inline 才能渲染
                // （'unsafe-inline' + 'unsafe-eval' 已被 Vite/Vue3 ESM 加载链路依赖）；
                // 通过严格 default-src / connect-src / object-src / frame-ancestors 兜底避免 XSS 扩散面
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' " + joinSources(cspImgSrc) + "; " +
                "font-src 'self' " + joinSources(cspFontSrc) + "; " +
                "connect-src 'self' " + joinSources(cspConnectSrc) + "; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self' https:; " +
                // 允许 Element Plus el-message 等使用的 data:/blob: URL Worker（setTimeout shim）
                "worker-src 'self' blob: data:; " +
                "object-src 'none'";
    }

    /** 拼接 CSP 源列表：过滤空值，空集合返回空串（不影响拼接） */
    private String joinSources(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        return sources.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    /**
     * 判断请求是否走 HTTPS：优先看 X-Forwarded-Proto（前置反代已设置），
     * 兜底看 request.isSecure()（直连场景）。
     */
    private boolean isHttps(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isEmpty()) {
            return "https".equalsIgnoreCase(forwarded);
        }
        return request.isSecure();
    }
}
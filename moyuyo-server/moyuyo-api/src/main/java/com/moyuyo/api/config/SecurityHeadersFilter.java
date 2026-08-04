package com.moyuyo.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 安全响应头过滤器
 * 补充浏览器侧安全防护响应头，缓解 XSS、点击劫持、MIME 嗅探、降级协议劫持等风险。
 * 仅设置响应头，不读取也不阻塞请求本体，开销可忽略。
 */
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 禁止浏览器嗅探响应 Content-Type，防止将非脚本资源当脚本执行
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 禁止页面被嵌入 iframe，缓解点击劫持（管理端 SPA 已用 SameSite Cookie，此处作为纵深防御）
        response.setHeader("X-Frame-Options", "DENY");
        // 启用浏览器内置 XSS 过滤器（旧版 IE/Chrome 仍生效，作为纵深防御）
        response.setHeader("X-XSS-Protection", "1; mode=block");
        // 强制 HTTPS（1 年，含子域名），生产环境必须启用 HTTPS
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        // 内容安全策略：限制资源加载来源，禁止内联脚本/样式以缓解 XSS
        // 允许同源 + HTTPS + data URI（图片）+ 特定 CDN；script 禁止内联与 unsafe-eval
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self' https:; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self' https:; " +
                "object-src 'none'");
        // 控制引用来源，防止从 Referer 泄露完整 URL 到第三方
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        // 禁用 Flash / PDF 嵌入插件，避免历史漏洞
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        // 隐私沙箱：限制跨源共享存储与 API
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");

        chain.doFilter(servletRequest, response);
    }
}

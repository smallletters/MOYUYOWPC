package com.moyuyo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.MediaUrlSigner;
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

/**
 * 私信媒体访问控制过滤器。
 * <p>
 * 仅拦截 {@code /uploads/dm/**}：校验 URL 上的 {@code e}（过期时间）与 {@code s}（HMAC 签名），
 * 签名由 {@link MediaUrlSigner} 在消息接口返回时签发。商品素材图等公开图片不走此过滤器。
 * <p>
 * 为什么不复用 JwtAuthFilter：{@code <image src>} 无法携带 Authorization 头，
 * 静态资源必须通过 URL 内签名做访问控制。
 */
@Slf4j
@RequiredArgsConstructor
public class UploadMediaAccessFilter implements Filter {

    private final MediaUrlSigner mediaUrlSigner;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = normalizePath(request.getRequestURI());
        // 非私信目录直接放行，避免影响商品图片等公开资源
        if (!mediaUrlSigner.isProtectedPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        // 预检请求不带签名，交给后续 CORS 处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String expiresAt = request.getParameter("e");
        String signature = request.getParameter("s");
        if (!mediaUrlSigner.verify(path, expiresAt, signature)) {
            log.warn("[uploads] 私信媒体签名校验失败 path={}", path);
            sendForbidden(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 规范化请求路径：解码 → 去矩阵参数 → 折叠 {@code //}、{@code .}、{@code ..}。
     * <p>
     * 必须与静态资源解析器的路径清理行为对齐，否则攻击者可用
     * {@code /uploads/./dm/xxx.jpg} 这类等价路径绕过 {@code /uploads/dm/} 前缀判断。
     */
    private String normalizePath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        String decoded;
        try {
            decoded = java.net.URLDecoder.decode(uri, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 非法编码保持原值，后续仍按路径段判断
            decoded = uri;
        }
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String segment : decoded.replace('\\', '/').split("/")) {
            // Spring MVC 默认 removeSemicolonContent=true，分号后的矩阵参数不参与映射
            int semicolon = segment.indexOf(';');
            String part = semicolon >= 0 ? segment.substring(0, semicolon) : segment;
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
                continue;
            }
            stack.addLast(part);
        }
        return "/" + String.join("/", stack);
    }

    /** 签名缺失/过期一律 403，不暴露文件是否存在 */
    private void sendForbidden(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(403);
        objectMapper.writeValue(response.getWriter(), Result.forbidden("无权访问该文件"));
    }
}

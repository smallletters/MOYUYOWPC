package com.moyuyo.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.Result;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Set;

/**
 * 写接口 Content-Type 强制过滤器
 * <p>
 * 背景：项目仅接收 JSON 请求，但当前没有任何过滤会拒绝 form-urlencoded / multipart / text-plain 等。
 * 攻击者可构造跨站表单（CSRF-like）触发 {@code POST /api/v1/auth/password/forgot} 等邮件轰炸接口。
 * <p>
 * 行为：
 * <ul>
 *   <li>仅对"写方法"（POST / PUT / PATCH）生效</li>
 *   <li>要求 Content-Type 为 {@code application/json} 或 {@code application/*+json}（如 application/vnd.api+json）</li>
 *   <li>缺失 / 不匹配的请求直接返回 415 Unsupported Media Type</li>
 *   <li>DELETE 方法放过 Content-Type 校验：DELETE 通常不携带 body（RFC 7231 §4.3.5），
 *       强制 Content-Type=application/json 反而会拦截符合规范的 RESTful 调用（fetch / axios 默认
 *       DELETE 不带 Content-Type 头）</li>
 *   <li>读取方法（GET/HEAD/OPTIONS）放行，避免影响文件下载与预检</li>
 *   <li>/admin/ 静态资源与 /api/admin/auth/login（兼容 form 登录未来扩展）不在拦截范围</li>
 * </ul>
 * <p>
 * 例外：{@code /api/v1/cs/upload}、{@code /api/v1/file/upload} 类文件上传接口需另行放行（multipart/form-data），
 * 可通过 {@link #EXCLUDED_PATH_PREFIXES} 白名单前缀维护。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ContentTypeFilter {

    private final ObjectMapper objectMapper;

    /** 需要强制 application/json 的写方法（不含 DELETE，详见类注释） */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH");

    /** 例外路径前缀（multipart 上传、文件导入等场景由具体接口自行校验） */
    private static final Set<String> EXCLUDED_PATH_PREFIXES = Set.of(
            "/api/v1/file/upload",
            "/api/v1/cs/upload",
            "/api/admin/batch-import/",
            // 管理后台文件上传（图片库 / 富文本编辑器）：multipart/form-data
            "/api/admin/upload/"
    );

    @Bean
    public FilterRegistrationBean<Filter> contentTypeFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InnerFilter(objectMapper));
        registration.addUrlPatterns("/api/*");
        // 关键修复：原 order=0 与 TraceIdFilter 同序，Spring 容器按 Bean 注册顺序决定实际执行顺序，
        // 出现"ContentType 拦截在 TraceId 之前导致 415 响应不带 traceId"的隐性时序漂移。
        // 调整为 order=-1000，确保 ContentTypeFilter 在 TraceIdFilter（order=0）之后、SecurityHeadersFilter（MIN_VALUE）之前执行。
        // 这样 ContentType 拦截时 traceId 已写入 MDC，415 响应也能正确带 traceId。
        registration.setOrder(-1000);
        return registration;
    }

    /** 内部过滤器类，避免污染 Config 自身的 Bean 生命周期 */
    @RequiredArgsConstructor
    static class InnerFilter implements Filter {
        private final ObjectMapper mapper;

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            HttpServletResponse response = (HttpServletResponse) servletResponse;

            String method = request.getMethod();
            if (!WRITE_METHODS.contains(method)) {
                chain.doFilter(request, response);
                return;
            }

            String path = request.getRequestURI();
            for (String prefix : EXCLUDED_PATH_PREFIXES) {
                if (path.startsWith(prefix)) {
                    chain.doFilter(request, response);
                    return;
                }
            }

            String contentType = request.getContentType();
            if (contentType == null || !isJsonContentType(contentType)) {
                // 仅 WARN 一次即可，避免高频扫描攻击（如攻击者反复发送非法 Content-Type 触发日志风暴）
                log.warn("Rejected non-JSON write request: method={}, path={}, contentType={}",
                        method, path, contentType);
                response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                response.setContentType("application/json;charset=UTF-8");
                Result<Void> result = Result.error(415, "仅支持 application/json 请求");
                mapper.writeValue(response.getWriter(), result);
                return;
            }

            chain.doFilter(request, response);
        }

        /** 匹配 application/json、application/xxx+json（RFC 6839 结构化语法后缀） */
        private boolean isJsonContentType(String contentType) {
            String main = contentType.split(";")[0].trim().toLowerCase();
            if ("application/json".equals(main)) {
                return true;
            }
            // 处理 application/vnd.api+json、application/hal+json 等
            int slash = main.indexOf('/');
            if (slash < 0) return false;
            String subtype = main.substring(slash + 1);
            return subtype.endsWith("+json");
        }
    }
}
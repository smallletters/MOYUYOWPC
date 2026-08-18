package com.moyuyo.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 单条 origin 最大长度：防注入 / 截断 / 浏览器响应头超长 */
    private static final int MAX_ORIGIN_LENGTH = 256;

    /** origin 总数上限：避免 Access-Control-Allow-Origin 响应头超长被浏览器截断 */
    private static final int MAX_ORIGIN_COUNT = 50;

    /**
     * 本地上传目录：与 AdminUploadController.uploadDir 保持一致（可通过 MOYUYO_UPLOAD_DIR 覆盖）。
     * <p>
     * 必须保证两个 classpath 路径完全一致，否则上传文件保存到 A 路径，但 WebMvcConfig 静态资源映射 B 路径，
     * 浏览器访问 /uploads/* 时 500（"URL cannot be resolved"）。
     */
    @Value("${moyuyo.upload.dir:/tmp/moyuyo-uploads}")
    private String uploadDir;

    /**
     * origin 字符白名单：scheme + 域名 + 端口，仅允许 [A-Za-z0-9\-\.\:/\?#=&%]
     * 拒绝含空白字符 / 控制字符 / DEL 的 origin，防御"origin 拼接注入"绕过攻击
     * （如 "https://moyuyo.com /evil.com"）
     */
    private static final Pattern ORIGIN_PATTERN = Pattern.compile(
            "^https?://[A-Za-z0-9][A-Za-z0-9\\-\\.]*(\\:[0-9]{1,5})?(/[^\\s]*)?$");

    // 生产环境必须通过环境变量 MOYUYO_CORS_ORIGINS 显式设置允许的前端域名
    @Value("${moyuyo.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        StringHttpMessageConverter converter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        converters.add(0, converter);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = parseOrigins(allowedOrigins);
        if (patterns.length == 0) {
            // 未配置跨域来源时不注册 CORS，避免空数组导致的意外开放
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Authorization", "Content-Type", "X-Trace-Id", "X-Sign", "X-Timestamp", "X-Nonce", "Accept", "Origin")
                .exposedHeaders("X-Trace-Id", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 解析跨域来源配置：
     * 1. 拆分逗号分隔列表
     * 2. 单条长度校验（≤256 字符），超长直接抛 IllegalStateException 阻断启动
     * 3. 单条字符白名单校验，拒绝含非法字符的 origin
     * 4. 去重 + 空过滤
     * <p>
     * 错误立即抛出而非静默丢弃：让运维在启动期就能感知配置错误，避免"启动成功但实际 CORS 不生效"的认知偏差
     */
    private String[] parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        String[] split = raw.split(",");
        if (split.length > MAX_ORIGIN_COUNT) {
            throw new IllegalStateException(
                    "MOYUYO_CORS_ORIGINS 条目数超过上限 " + MAX_ORIGIN_COUNT + "（实际 " + split.length + "），请精简白名单");
        }
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>(split.length);
        for (String origin : split) {
            String trimmed = origin.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 单条长度校验：与 ProdConfigValidator 启动期校验语义对齐
            if (trimmed.length() > MAX_ORIGIN_LENGTH) {
                throw new IllegalStateException(
                        "CORS origin 长度超过 " + MAX_ORIGIN_LENGTH + " 字符（实际 " + trimmed.length() + "）：" + trimmed);
            }
            // 字符白名单校验：防御 origin 拼接注入
            if (!ORIGIN_PATTERN.matcher(trimmed).matches()) {
                throw new IllegalStateException(
                        "CORS origin 含非法字符或格式不合法（必须以 http/https 开头）：" + trimmed);
            }
            // 拒绝通配符（与 allowCredentials 共存时 CORS 规范禁止 *）
            if ("*".equals(trimmed)) {
                throw new IllegalStateException("CORS origin 不允许设置为 '*'（与 allowCredentials 冲突）");
            }
            if (seen.add(trimmed)) {
                result.add(trimmed);
            }
        }
        return result.toArray(new String[0]);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 注意：admin SPA 入口的 no-cache 策略通过 application.yml 中的
        // spring.web.resources.cache.cachecontrol 配置控制（index.html 需要 no-cache，
        // 而 js/css 自带 hash 享受 immutable 缓存）。这里只做资源位置与链声明。
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .resourceChain(true);
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true);
        // 本地上传目录：与 AdminUploadController.uploadDir 保持一致（通过 MOYUYO_UPLOAD_DIR 覆盖）
        // 使用 toAbsolutePath() 把相对路径 / Linux / Windows 路径都规范化为绝对路径，再加 file: 前缀
        // 关键修复：原代码硬编码 "file:/tmp/moyuyo-uploads/"，但 Windows dev 环境默认上传到 D:\tmp\moyuyo-uploads\，
        // 两者不一致导致 500（"URL cannot be resolved in the file system"）
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/")
                .resourceChain(true);
    }

    /**
     * SPA 前端路由 fallback：Vue Router 使用 history 模式时，刷新非根路径（如 /admin/orders/123）
     * 会让 Tomcat 收到真实请求，由 ViewController 转发到 /admin/index.html 让前端路由接管。
     * <p>
     * 与 ResourceHandler 互补：ResourceHandler 仅处理真实存在的静态资源（.js/.css/.png 等），
     * fallback 仅处理"路径不带 . 后缀的非 API 请求"，避免误吞 /api/* 请求。
     * <p>
     * 关键限制：仅匹配无扩展名的 path，防止 /admin/favicon.ico 等真实静态资源被错误转发到 SPA index.html。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 关键修复：/admin 与 /admin/ 都要转发（带 / 与不带 / 是两个 URL），避免双斜杠路径
        registry.addViewController("/admin").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        // SPA history 模式 fallback：前端 router.push('/dashboard') / '/orders/123' 等
        // 子路径必须全部回到 index.html 让前端路由接管，否则登录后跳转会 404 / 被守卫拦截回登录页
        registry.addViewController("/admin/{path:^(?!.*\\.).*$}/**")
                .setViewName("forward:/admin/index.html");
    }
}
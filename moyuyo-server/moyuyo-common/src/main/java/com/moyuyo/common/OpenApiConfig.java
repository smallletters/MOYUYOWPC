package com.moyuyo.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SpringDoc OpenAPI 配置
 * <p>
 * 关键修复：
 * - 读取 application.yml 中的 moyuyo.openapi.{contact-email, version, servers}，
 *   避免硬编码版本号与联系邮箱导致运维改配置后 Swagger UI 仍展示旧值
 * - servers 支持 "url|description" 格式（与 .env.example OPENAPI_SERVERS 对齐）
 * - 补充 License 与 License URL，便于合规审计与品牌露出
 */
@Configuration
public class OpenApiConfig {

    @Value("${moyuyo.openapi.contact-email:backend@your-company.example.com}")
    private String contactEmail;

    @Value("${moyuyo.openapi.version:1.0.0}")
    private String apiVersion;

    @Value("${moyuyo.openapi.servers:}")
    private String serversConfig;

    /**
     * License URL：用于在 OpenAPI 文档中暴露 License 信息，便于合规审计与品牌露出。
     * <p>
     * P1 修复：原 License 是否显示与 contactEmail 配置项耦合，不符合"独立配置项独立控制"的语义。
     * 现改为独立 {@code moyuyo.openapi.license-url} 配置开关，仅当其非空时附加 License。
     */
    @Value("${moyuyo.openapi.license-url:}")
    private String licenseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        Info info = new Info()
                .title("MOYUYO API")
                .version(apiVersion)
                .description("MOYUYO 宠物用品商城后端接口文档")
                .contact(new Contact()
                        .name("MOYUYO Backend Team")
                        .email(contactEmail));
        // P1 修复：License 字段附加条件由"contactEmail 非空"改为独立的 licenseUrl 配置开关。
        // 旧逻辑将 License 显示与否与 contactEmail 耦合：运维若临时清空 contactEmail 占位会导致 License 一同消失，
        // 不符合"独立配置项独立控制"的语义。新增 moyuyo.openapi.license-url 配置，
        // 仅当其非空时附加 License，便于合规审计与品牌露出可独立开关。
        if (StringUtils.hasText(licenseUrl)) {
            info.license(new License()
                    .name("Proprietary")
                    .url(licenseUrl));
        }
        OpenAPI openAPI = new OpenAPI().info(info);
        // 解析 OPENAPI_SERVERS："url|description" 用 | 分隔 url 与 description；
        // 多组用英文逗号分隔；空白段自动跳过；URL 必须 http/https 开头
        List<Server> servers = parseServers(serversConfig);
        if (!servers.isEmpty()) {
            openAPI.servers(servers);
        }
        return openAPI;
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/**")
                .build();
    }

    /**
     * 解析 OPENAPI_SERVERS 字符串为 OpenAPI Server 列表
     * <p>
     * 输入格式：{@code "https://api.moyuyo.com|生产,https://staging.moyuyo.com|预发"}
     * <p>
     * 解析规则：
     * - 多个 Server 用英文逗号分隔
     * - 单个 Server 内 url 与 description 用 | 分隔（| 可选）
     * - 空白段自动跳过
     * - url 必须以 http:// 或 https:// 开头，否则跳过（防误配）
     */
    static List<Server> parseServers(String raw) {
        List<Server> list = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return list;
        }
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sep = trimmed.indexOf('|');
            String url = (sep >= 0 ? trimmed.substring(0, sep) : trimmed).trim();
            String description = (sep >= 0 ? trimmed.substring(sep + 1) : "").trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                continue;
            }
            Server server = new Server().url(url);
            // 使用 StringUtils.hasText 统一空白字符检测，与上文 StringUtils.hasText(raw) 校验保持一致
            if (StringUtils.hasText(description)) {
                server.description(description);
            }
            list.add(server);
        }
        return list;
    }
}

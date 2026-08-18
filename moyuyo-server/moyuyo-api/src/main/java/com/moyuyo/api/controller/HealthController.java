package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 * <p>
 * 生产环境 K8s 探针优先使用独立端口 9090 上的 /actuator/health（详见 application-prod.yml）；
 * 本端点仅作为业务端口上的轻量回包探活，不暴露任何组件细节。
 * <p>
 * 安全要点：
 * <ul>
 *   <li>禁用缓存：避免 CDN / 反代缓存 OK 响应导致真实故障被掩盖</li>
 *   <li>禁用 Server/X-Powered-By 头：由 SecurityHeadersFilter 统一处理，本控制器无需关注</li>
 *   <li>仅返回 OK 字符串：不含时间戳 / 版本 / 节点名，防止侧信道泄露部署信息</li>
 * </ul>
 */
@Tag(name = "系统管理")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查（业务端口轻量探活，生产环境 K8s 探针应使用 9090/actuator/health）")
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<String> health(HttpServletResponse response) {
        // 禁用缓存：避免反代 / 浏览器缓存 OK 响应导致真实故障被掩盖
        // no-store 指令比 max-age=0 更严格（部分 CDN / 中间件会忽略 max-age=0，
        // 但 no-store 必须在所有缓存层都生效）。
        // 同时下发 Pragma: no-cache 兼容 HTTP/1.0 旧代理（部分老旧反代仅识别 Pragma）。
        response.setHeader("Cache-Control", CacheControl.noStore().cachePrivate().getHeaderValue());
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        return Result.success("OK");
    }
}

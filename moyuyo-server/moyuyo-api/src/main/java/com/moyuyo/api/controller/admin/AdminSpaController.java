package com.moyuyo.api.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class AdminSpaController {

    // 静态资源根目录，用于校验解析后的路径不会越界
    private static final String STATIC_ADMIN_ROOT = "static/admin/";

    @GetMapping({"/admin", "/admin/", "/admin/index.html"})
    public ResponseEntity<byte[]> adminRoot() throws IOException {
        return getIndexHtml();
    }

    @GetMapping("/admin/assets/{filename:.+}")
    public ResponseEntity<byte[]> adminAsset(@PathVariable String filename) throws IOException {
        return serveResource(STATIC_ADMIN_ROOT + "assets/" + filename);
    }

    @GetMapping("/admin/assets/{dir}/{filename:.+}")
    public ResponseEntity<byte[]> adminAssetWithDir(@PathVariable String dir, @PathVariable String filename) throws IOException {
        return serveResource(STATIC_ADMIN_ROOT + "assets/" + dir + "/" + filename);
    }

    // 单层路径：如 /admin/dashboard
    @GetMapping("/admin/{path}")
    public ResponseEntity<byte[]> adminPath(@PathVariable String path) throws IOException {
        if (path.contains(".")) {
            return serveResource(STATIC_ADMIN_ROOT + path);
        }
        return getIndexHtml();
    }

    // 2 层路径：如 /admin/orders/123
    @GetMapping("/admin/{path}/{subpath}")
    public ResponseEntity<byte[]> adminPathWithSubpath(
            @PathVariable String path,
            @PathVariable String subpath) throws IOException {
        if (subpath.contains(".")) {
            return serveResource(STATIC_ADMIN_ROOT + path + "/" + subpath);
        }
        return getIndexHtml();
    }

    // 3+ 层路径 fallback（如 /admin/products/edit/123、/admin/orders/123/items）
    // 任何未匹配的 admin 子路径都返回 SPA index.html，由前端路由接管
    @GetMapping("/admin/**")
    public ResponseEntity<byte[]> adminDeepPath(HttpServletRequest request) throws IOException {
        String fullPath = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (fullPath == null) {
            fullPath = request.getRequestURI();
        }
        // 仅当请求路径以 /admin/ 开头时处理
        String suffix = fullPath.startsWith("/admin/") ? fullPath.substring("/admin/".length()) : fullPath;
        // 静态资源（assets/、包含扩展名）走静态资源
        if (suffix.startsWith("assets/") || suffix.contains(".")) {
            return serveResource(STATIC_ADMIN_ROOT + suffix);
        }
        return getIndexHtml();
    }

    private ResponseEntity<byte[]> getIndexHtml() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/admin/index.html");
        byte[] content = resource.getInputStream().readAllBytes();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .contentLength(content.length)
                .body(content);
    }

    /**
     * 安全地加载静态资源，防止路径遍历攻击。
     * 通过拒绝包含路径遍历字符（../、..\、绝对路径前缀）的输入，
     * 并校验规范化路径仍位于 static/admin/ 目录下，避免读取任意文件。
     */
    private ResponseEntity<byte[]> serveResource(String resourcePath) throws IOException {
        // 1. 显式拒绝路径遍历字符，避免绕过
        if (isPathTraversal(resourcePath)) {
            return ResponseEntity.notFound().build();
        }

        // 2. 规范化路径，再次校验仍位于允许的根目录下（纵深防御）
        String normalized = resourcePath.replace('\\', '/');
        if (!normalized.startsWith(STATIC_ADMIN_ROOT)) {
            return ResponseEntity.notFound().build();
        }

        ClassPathResource resource = new ClassPathResource(normalized);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = resource.getInputStream().readAllBytes();
        String contentType = getContentType(normalized);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(content.length)
                .body(content);
    }

    /**
     * 检测路径遍历字符：包含 ".." 段、绝对路径前缀或反斜杠转义，均视为危险输入。
     */
    private boolean isPathTraversal(String path) {
        if (path == null || path.isEmpty()) {
            return true;
        }
        // 统一分隔符后检查
        String normalized = path.replace('\\', '/');
        // 含有 ".." 段（如 ../、 /..、 ..）
        if (normalized.contains("../") || normalized.contains("/..")
                || normalized.equals("..") || normalized.startsWith("../")) {
            return true;
        }
        // 绝对路径或 URL 协议前缀
        if (normalized.startsWith("/") || normalized.contains("://")) {
            return true;
        }
        // Windows 驱动器前缀（如 C:）
        if (normalized.length() >= 2 && normalized.charAt(1) == ':') {
            return true;
        }
        return false;
    }

    private String getContentType(String path) {
        if (path.endsWith(".js")) {
            return "application/javascript";
        } else if (path.endsWith(".css")) {
            return "text/css";
        } else if (path.endsWith(".html")) {
            return "text/html";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (path.endsWith(".woff") || path.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }
}

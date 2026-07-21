package com.moyuyo.api.controller.admin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class AdminSpaController {

    @GetMapping("/admin")
    public ResponseEntity<byte[]> adminRoot() throws IOException {
        return getIndexHtml();
    }

    @GetMapping("/admin/index.html")
    public ResponseEntity<byte[]> adminIndex() throws IOException {
        return getIndexHtml();
    }

    @GetMapping("/admin/assets/{filename:.+}")
    public ResponseEntity<byte[]> adminAsset(@PathVariable String filename) throws IOException {
        return serveResource("static/admin/assets/" + filename);
    }

    @GetMapping("/admin/assets/{dir}/{filename:.+}")
    public ResponseEntity<byte[]> adminAssetWithDir(@PathVariable String dir, @PathVariable String filename) throws IOException {
        return serveResource("static/admin/assets/" + dir + "/" + filename);
    }

    @GetMapping("/admin/{path}")
    public ResponseEntity<byte[]> adminPath(@PathVariable String path) throws IOException {
        if (path.contains(".")) {
            return serveResource("static/admin/" + path);
        }
        return getIndexHtml();
    }

    @GetMapping("/admin/{path}/{subpath}")
    public ResponseEntity<byte[]> adminPathWithSubpath(
            @PathVariable String path, 
            @PathVariable String subpath) throws IOException {
        if (subpath.contains(".")) {
            return serveResource("static/admin/" + path + "/" + subpath);
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

    private ResponseEntity<byte[]> serveResource(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = resource.getInputStream().readAllBytes();
        String contentType = getContentType(resourcePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(content.length)
                .body(content);
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

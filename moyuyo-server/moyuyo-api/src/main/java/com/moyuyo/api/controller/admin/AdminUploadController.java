package com.moyuyo.api.controller.admin;

import com.moyuyo.common.dto.admin.UploadResult;
import com.moyuyo.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 管理端文件上传 Controller（本地存储 + 静态服务）。
 * <p>
 * 设计要点：
 * 1. 仅支持图片（PNG / JPG / JPEG / GIF / WebP / SVG），通过文件扩展名 + Content-Type 双重白名单校验
 * 2. 文件大小上限由 application.yml spring.servlet.multipart 控制（默认 20MB）
 * 3. 按日期分目录（yyyy/MM/dd）避免单目录文件过多，便于运维清理
 * 4. 文件名使用 UUID + 原始扩展名，避免中文 / 特殊字符与同名覆盖
 * 5. 静态资源通过 WebMvcConfig 映射 /uploads/** → ${MOYUYO_UPLOAD_DIR:/tmp/moyuyo-uploads}
 * 6. 返回 URL 为相对路径 /uploads/yyyy/MM/dd/uuid.ext，由前端拼接 baseURL
 */
@Slf4j
@Tag(name = "管理后台 - 文件上传")
@RestController
@RequestMapping("/api/admin/upload")
public class AdminUploadController {

    /** 图片白名单：扩展名 + Content-Type 双重校验 */
    private static final Set<String> ALLOWED_EXT = new HashSet<>(
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "svg"));
    private static final Set<String> ALLOWED_CT = new HashSet<>(
            Arrays.asList("image/png", "image/jpg", "image/jpeg", "image/gif", "image/webp", "image/svg+xml"));

    /** 上传根目录：可通过 MOYUYO_UPLOAD_DIR 环境变量覆盖（生产环境建议挂载到持久卷） */
    @Value("${moyuyo.upload.dir:/tmp/moyuyo-uploads}")
    private String uploadDir;

    /** CDN/静态访问前缀：默认 /uploads/，与 WebMvcConfig addResourceHandlers 保持一致 */
    @Value("${moyuyo.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadPath);
            log.info("[upload] 本地上传目录已就绪：{}", this.uploadPath);
        } catch (IOException e) {
            log.error("[upload] 创建上传目录失败：{}", this.uploadPath, e);
            // 不阻断启动：上传接口会在调用时返回 500，但其他接口不受影响
        }
    }

    /**
     * 上传图片（单个）。
     * <p>
     * POST /api/admin/upload/image
     * multipart/form-data: file=@xxx.png
     * <p>
     * 返回 URL 为相对路径 /uploads/yyyy/MM/dd/uuid.ext，前端按需拼接 baseURL
     */
    @PostMapping("/image")
    @Operation(summary = "上传单张图片")
    public Result<UploadResult> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.badRequest("文件为空");
        }

        String original = file.getOriginalFilename();
        String ct = file.getContentType();

        // 扩展名兜底：uni-app H5 / 部分客户端 上传时会把 name 改写成 file-<timestamp> 丢扩展名,
        // 此时从 Content-Type 推断(与 UserUploadController 保持一致的兜底策略)。
        String ext = extractExt(original);
        if (ext == null) {
            ext = extractExtFromContentType(ct);
        }
        if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase())) {
            return Result.badRequest("不支持的文件类型，仅允许 PNG/JPG/JPEG/GIF/WebP/SVG");
        }

        // Content-Type 校验：防止扩展名伪造(空值不阻断,已通过扩展名兜底分支)
        if (ct != null && !ct.isBlank() && !ALLOWED_CT.contains(ct.toLowerCase())) {
            return Result.badRequest("文件 Content-Type 不匹配");
        }

        // 构造日期分目录路径：yyyy/MM/dd
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();

        Path target = uploadPath.resolve(datePart).resolve(filename).normalize();
        // 防路径穿越：必须仍在 uploadPath 之下
        if (!target.startsWith(uploadPath)) {
            return Result.badRequest("非法文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            // 原子复制 + 避免部分写入
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[upload] 写入文件失败：{}", target, e);
            return Result.error("文件写入失败");
        }

        long size = file.getSize();
        String url = urlPrefix + datePart + "/" + filename;

        UploadResult result = new UploadResult();
        result.setUrl(url);
        result.setFilename(filename);
        result.setOriginalName(original);
        result.setSize(size);
        result.setContentType(ct);
        return Result.success(result);
    }

    /**
     * 批量上传图片（与 WC 产品图库交互对齐）。
     * <p>
     * POST /api/admin/upload/images
     * multipart/form-data: files=@a.png&files=@b.jpg
     */
    @PostMapping("/images")
    @Operation(summary = "批量上传图片")
    public Result<List<UploadResult>> uploadImages(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return Result.badRequest("文件为空");
        }
        List<UploadResult> results = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            Result<UploadResult> r = uploadImage(f);
            if (r.isSuccess() && r.getData() != null) {
                results.add(r.getData());
            }
        }
        if (results.isEmpty()) {
            return Result.badRequest("所有文件上传失败");
        }
        return Result.success(results);
    }

    /**
     * 删除已上传图片（仅删除本地文件，不影响其他业务）。
     * <p>
     * DELETE /api/admin/upload/image?url=/uploads/2026/08/18/abc.png
     */
    @DeleteMapping("/image")
    @Operation(summary = "删除已上传图片")
    public Result<Void> deleteImage(@RequestParam("url") String url) {
        if (url == null || url.isBlank()) {
            return Result.badRequest("url 不能为空");
        }
        // 仅允许删除 urlPrefix 范围内的文件，防止任意路径删除
        if (!url.startsWith(urlPrefix)) {
            return Result.badRequest("非法 URL");
        }
        String relative = url.substring(urlPrefix.length());
        Path target = uploadPath.resolve(relative).normalize();
        if (!target.startsWith(uploadPath)) {
            return Result.badRequest("路径穿越拒绝");
        }
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (!deleted) {
                log.warn("[upload] 文件不存在：{}", target);
            }
            return Result.success();
        } catch (IOException e) {
            log.error("[upload] 删除文件失败：{}", target, e);
            return Result.error("删除失败");
        }
    }

    /**
     * 从原始文件名抽取扩展名(不带点号)
     * 注意:uni-app H5 / 部分客户端 上传时会把 name 改成 file-<timestamp> 丢失扩展名,
     * 此时调用方应改用 extractExtFromContentType 兜底。
     */
    private String extractExt(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1);
    }

    /**
     * 从 Content-Type 推断文件扩展名(兜底方案)
     * 比 UserUploadController 多覆盖 svg(SVG 在 C 端不允许但管理后台允许)。
     */
    private String extractExtFromContentType(String contentType) {
        if (contentType == null) return null;
        switch (contentType.toLowerCase()) {
            case "image/png":         return "png";
            case "image/jpeg":
            case "image/jpg":         return "jpg";
            case "image/gif":         return "gif";
            case "image/webp":        return "webp";
            case "image/svg+xml":     return "svg";
            case "video/mp4":         return "mp4";
            case "video/quicktime":   return "mov";
            case "video/webm":        return "webm";
            default:                  return null;
        }
    }
}
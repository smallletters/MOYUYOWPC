package com.moyuyo.api.controller;

import com.moyuyo.common.dto.admin.UploadResult;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
 * 用户端文件上传 Controller（社区发帖/客服/反馈 等场景使用）。
 *
 * 设计要点：
 * 1. 仅支持图片（PNG/JPG/JPEG/GIF/WebP），通过文件扩展名 + Content-Type 双重白名单校验
 * 2. 复用 AdminUploadController 的 moyuyo.upload.dir / moyuyo.upload.url-prefix 配置，
 *    同一目录上传、WebMvcConfig 已映射 /uploads/** 静态资源
 * 3. 要求登录态：通过 UserContextHolder 隐式校验（被 spring-security 拦截器保护）
 * 4. ContentTypeFilter 已为 /api/v1/file/upload 前缀放行 multipart/form-data
 */
@Slf4j
@Tag(name = "用户端 - 文件上传")
@RestController
@RequestMapping("/api/v1/file/upload")
public class UserUploadController {

    /** 图片白名单：扩展名 + Content-Type 双重校验（与 AdminUploadController 一致） */
    private static final Set<String> ALLOWED_EXT = new HashSet<>(
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp"));
    private static final Set<String> ALLOWED_CT = new HashSet<>(
            Arrays.asList("image/png", "image/jpg", "image/jpeg", "image/gif", "image/webp"));

    /** 视频白名单：仅允许 mp4/mov/webm，与主流短视频一致；大小上限单独由 spring.servlet.multipart 控制 */
    private static final Set<String> ALLOWED_VIDEO_EXT = new HashSet<>(
            Arrays.asList("mp4", "mov", "webm"));
    private static final Set<String> ALLOWED_VIDEO_CT = new HashSet<>(
            Arrays.asList("video/mp4", "video/quicktime", "video/webm"));

    /** 与 AdminUploadController 共用 moyuyo.upload.dir 默认值 */
    @Value("${moyuyo.upload.dir:/tmp/moyuyo-uploads}")
    private String uploadDir;

    /** 与 AdminUploadController 共用 moyuyo.upload.url-prefix 默认值 */
    @Value("${moyuyo.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadPath);
            log.info("[user-upload] 本地上传目录已就绪：{}", this.uploadPath);
        } catch (IOException e) {
            log.error("[user-upload] 创建上传目录失败：{}", this.uploadPath, e);
        }
    }

    /**
     * 上传单张图片（社区发帖/客服/反馈 通用）。
     * <p>
     * POST /api/v1/file/upload/image
     * multipart/form-data: file=@xxx.png
     * <p>
     * 返回 URL 为相对路径 /uploads/yyyy/MM/dd/uuid.ext，前端 dev 走 vite proxy、
     * prod nginx 反代 /uploads/** → 后端静态目录。
     */
    @PostMapping("/image")
    @Operation(summary = "用户端上传单张图片")
    public Result<UploadResult> uploadImage(@RequestParam("file") MultipartFile file) {
        // 隐式登录态校验:未登录时 UserContextHolder.getUserId() 抛异常被全局处理器转 401
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (file == null || file.isEmpty()) {
            return Result.badRequest("文件为空");
        }

        String original = file.getOriginalFilename();
        String ct = file.getContentType();

        // 扩展名判定：优先从文件名取,uni-app H5 / 部分客户端 会丢扩展名
        // (filename 被改写成 file-<timestamp>),此时从 Content-Type 兜底推断。
        String ext = extractExt(original);
        if (ext == null) {
            ext = extractExtFromContentType(ct);
        }
        if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase())) {
            return Result.badRequest("不支持的文件类型，仅允许 PNG/JPG/JPEG/GIF/WebP");
        }

        // Content-Type 校验：防止扩展名伪造（仅在有值时校验,空 Content-Type 不阻断）
        if (ct != null && !ct.isBlank() && !ALLOWED_CT.contains(ct.toLowerCase())) {
            return Result.badRequest("文件 Content-Type 不匹配");
        }

        // 构造日期分目录路径：yyyy/MM/dd
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();

        Path target = uploadPath.resolve(datePart).resolve(filename).normalize();
        // 防路径穿越
        if (!target.startsWith(uploadPath)) {
            return Result.badRequest("非法文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[user-upload] 写入文件失败：{}", target, e);
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
        log.info("[user-upload] userId={} uploaded {} bytes -> {}", userId, size, url);
        return Result.success(result);
    }

    /**
     * 批量上传图片（最多 9 张，对齐社区发布器上限）。
     * <p>
     * POST /api/v1/file/upload/images
     * multipart/form-data: files=@a.png&files=@b.jpg
     */
    @PostMapping("/images")
    @Operation(summary = "用户端批量上传图片")
    public Result<List<UploadResult>> uploadImages(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return Result.badRequest("文件为空");
        }
        if (files.length > 9) {
            return Result.badRequest("单次最多上传 9 张");
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
     * 用于 uni-app H5 / 部分客户端 上传时文件名被改写为 file-<timestamp> 的场景。
     * 映射不全,只覆盖主流图片/视频类型,未覆盖的返回 null。
     */
    private String extractExtFromContentType(String contentType) {
        if (contentType == null) return null;
        switch (contentType.toLowerCase()) {
            // 图片
            case "image/png":         return "png";
            case "image/jpeg":
            case "image/jpg":         return "jpg";
            case "image/gif":         return "gif";
            case "image/webp":        return "webp";
            // 视频
            case "video/mp4":         return "mp4";
            case "video/quicktime":   return "mov";
            case "video/webm":        return "webm";
            default:                  return null;
        }
    }

    /**
     * 上传视频（社区发帖）。
     * <p>
     * POST /api/v1/file/upload/video
     * multipart/form-data: file=@xxx.mp4
     * <p>
     * 校验：
     * <ul>
     *   <li>扩展名白名单 mp4 / mov / webm</li>
     *   <li>Content-Type 二次校验（防伪造）</li>
     *   <li>文件大小上限由 application.yml spring.servlet.multipart.max-file-size 控制</li>
     * </ul>
     * 返回结构与 {@code uploadImage} 一致，方便前端统一处理。
     */
    @PostMapping("/video")
    @Operation(summary = "用户端上传单个视频")
    public Result<UploadResult> uploadVideo(@RequestParam("file") MultipartFile file) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (file == null || file.isEmpty()) {
            return Result.badRequest("文件为空");
        }

        String original = file.getOriginalFilename();
        String ct = file.getContentType();

        // 扩展名兜底：uni-app H5 视频上传同样可能丢扩展名,从 Content-Type 推断
        String ext = extractExt(original);
        if (ext == null) {
            ext = extractExtFromContentType(ct);
        }
        if (ext == null || !ALLOWED_VIDEO_EXT.contains(ext.toLowerCase())) {
            return Result.badRequest("不支持的视频格式，仅允许 MP4/MOV/WebM");
        }

        if (ct != null && !ct.isBlank() && !ALLOWED_VIDEO_CT.contains(ct.toLowerCase())) {
            return Result.badRequest("视频 Content-Type 不匹配");
        }

        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();

        Path target = uploadPath.resolve(datePart).resolve(filename).normalize();
        if (!target.startsWith(uploadPath)) {
            return Result.badRequest("非法文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[user-upload] 写入视频失败：{}", target, e);
            return Result.error("视频写入失败");
        }

        long size = file.getSize();
        String url = urlPrefix + datePart + "/" + filename;

        UploadResult result = new UploadResult();
        result.setUrl(url);
        result.setFilename(filename);
        result.setOriginalName(original);
        result.setSize(size);
        result.setContentType(ct);
        log.info("[user-upload] userId={} uploaded video {} bytes -> {}", userId, size, url);
        return Result.success(result);
    }
}
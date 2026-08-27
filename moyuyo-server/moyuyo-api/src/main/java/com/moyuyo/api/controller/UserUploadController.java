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

        // 校验原始文件名扩展名
        String original = file.getOriginalFilename();
        String ext = extractExt(original);
        if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase())) {
            return Result.badRequest("不支持的文件类型，仅允许 PNG/JPG/JPEG/GIF/WebP");
        }

        // Content-Type 校验：防止扩展名伪造
        String ct = file.getContentType();
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

    private String extractExt(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1);
    }
}
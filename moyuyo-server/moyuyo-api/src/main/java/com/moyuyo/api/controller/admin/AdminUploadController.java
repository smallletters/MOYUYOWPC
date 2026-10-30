package com.moyuyo.api.controller.admin;

import com.moyuyo.common.dto.admin.UploadPage;
import com.moyuyo.common.dto.admin.UploadResult;
import com.moyuyo.common.dto.admin.UploadedFile;
import com.moyuyo.common.Result;
import com.moyuyo.service.admin.AdminUploadGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
@RequiredArgsConstructor
public class AdminUploadController {

    /** 图片空间分组服务：维护「图片 URL -> 分组」映射 */
    private final AdminUploadGroupService adminUploadGroupService;

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
     * multipart/form-data: file=@xxx.png&groupId=12（groupId 可选，指定则归入该分组）
     * <p>
     * 返回 URL 为相对路径 /uploads/yyyy/MM/dd/uuid.ext，前端按需拼接 baseURL
     */
    @PostMapping("/image")
    @Operation(summary = "上传单张图片")
    public Result<UploadResult> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long groupId) {
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

        // 记录图片与分组归属：失败不影响上传结果，可在图片空间重新分组
        try {
            adminUploadGroupService.bindOnUpload(url, groupId);
        } catch (Exception e) {
            log.warn("[upload] 记录图片分组失败：url={}, groupId={}", url, groupId, e);
        }

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
     * multipart/form-data: files=@a.png&files=@b.jpg&groupId=12（groupId 可选）
     */
    @PostMapping("/images")
    @Operation(summary = "批量上传图片")
    public Result<List<UploadResult>> uploadImages(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(required = false) Long groupId) {
        if (files == null || files.length == 0) {
            return Result.badRequest("文件为空");
        }
        List<UploadResult> results = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            Result<UploadResult> r = uploadImage(f, groupId);
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
     * 分页查询已上传图片（图片空间）。
     * <p>
     * GET /api/admin/upload/list?page=1&size=24&groupId=12
     * <p>
     * groupId 传参语义：省略 / 负数 = 全部；0 = 未分组；正数 = 指定分组。
     * <p>
     * 说明：图片按日期目录存放，这里直接扫描上传根目录并按最后修改时间倒序分页。
     * 管理端查询频率低、文件量级有限，扫描可接受；若未来图片数量达到十万级，
     * 建议改为在 mo_upload_file 表中维护索引，避免每次全量遍历目录。
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询已上传图片")
    public Result<UploadPage> listImages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) Long groupId) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 24;
        }

        List<UploadedFile> all = new ArrayList<>();
        if (Files.isDirectory(uploadPath)) {
            // 目录层级固定为 yyyy/MM/dd/文件名，maxDepth=4 防止扫描到无关的深层目录
            try (Stream<Path> stream = Files.walk(uploadPath, 4)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> {
                            String ext = extractExt(p.getFileName().toString());
                            return ext != null && ALLOWED_EXT.contains(ext.toLowerCase());
                        })
                        .forEach(p -> all.add(toUploadedFile(p)));
            } catch (IOException | UncheckedIOException e) {
                // Files.walk 为惰性遍历，迭代中的 I/O 异常会被包装成 UncheckedIOException
                log.error("[upload] 扫描上传目录失败：{}", uploadPath, e);
                return Result.error("读取图片目录失败");
            }
        }

        // 合并分组归属：未建立映射的图片视为「未分组」
        Map<String, Long> groupByUrl = Map.of();
        try {
            groupByUrl = adminUploadGroupService.groupIdByUrl();
        } catch (Exception e) {
            // 分组表异常时不影响图片浏览，全部按未分组展示
            log.warn("[upload] 查询图片分组映射失败，按未分组展示", e);
        }
        for (UploadedFile item : all) {
            item.setGroupId(groupByUrl.get(item.getUrl()));
        }

        // 按分组筛选：null / 负数 = 全部，0 = 未分组，正数 = 指定分组
        if (groupId != null && groupId > 0) {
            all.removeIf(item -> !groupId.equals(item.getGroupId()));
        } else if (groupId != null && groupId == 0) {
            all.removeIf(item -> item.getGroupId() != null);
        }

        // 最新上传的图片排在最前，便于运营快速找到刚上传的图片
        all.sort((a, b) -> Long.compare(b.getLastModified(), a.getLastModified()));

        // 统计总占用：用普通循环，避免 Stream 方法引用在 IDE 空值分析下产生告警
        long totalSize = 0L;
        for (UploadedFile item : all) {
            totalSize += item.getSize();
        }
        long offset = (long) (page - 1) * size;
        int from = (int) Math.min(offset, all.size());
        int to = Math.min(from + size, all.size());

        UploadPage result = new UploadPage();
        result.setRecords(all.subList(from, to));
        result.setTotal(all.size());
        result.setPage(page);
        result.setSize(size);
        result.setTotalSize(totalSize);
        return Result.success(result);
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
            // 同步清理分组映射（即使文件已不存在也清理，避免产生脏数据）
            try {
                adminUploadGroupService.unbindOnDelete(url);
            } catch (Exception e) {
                log.warn("[upload] 清理图片分组映射失败：url={}", url, e);
            }
            return Result.success();
        } catch (IOException e) {
            log.error("[upload] 删除文件失败：{}", target, e);
            return Result.error("删除失败");
        }
    }

    /**
     * 将磁盘文件转换为图片空间列表项（URL 统一使用 / 分隔，兼容 Windows）。
     */
    private UploadedFile toUploadedFile(Path file) {
        UploadedFile item = new UploadedFile();
        String relative = uploadPath.relativize(file).toString().replace('\\', '/');
        item.setUrl(urlPrefix + relative);
        item.setFilename(file.getFileName().toString());
        try {
            item.setSize(Files.size(file));
            item.setLastModified(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            // 单个文件属性读取失败不影响整个列表，降级为 0
            log.warn("[upload] 读取文件属性失败：{}", file, e);
            item.setSize(0L);
            item.setLastModified(0L);
        }
        return item;
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
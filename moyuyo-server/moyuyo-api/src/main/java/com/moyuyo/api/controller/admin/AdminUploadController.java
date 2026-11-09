package com.moyuyo.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.admin.UploadPage;
import com.moyuyo.common.dto.admin.UploadResult;
import com.moyuyo.common.dto.admin.UploadedFile;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.dao.admin.entity.UploadFileEntity;
import com.moyuyo.dao.admin.mapper.UploadFileMapper;
import com.moyuyo.dao.mapper.DmConversationMapper;
import com.moyuyo.dao.mapper.DmMessageMapper;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 管理端文件上传 Controller（本地存储 + 静态服务）。
 * <p>
 * 设计要点：
 * 1. 仅支持图片（PNG / JPG / JPEG / GIF / WebP），通过文件扩展名 + Content-Type 双重白名单校验
 *    2026-11 安全修复：移除 SVG —— SVG 可携带内联脚本，作为同源静态资源直接打开会形成存储型 XSS
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

    /** 私信消息 Mapper：用于从图片空间排除用户聊天图片 */
    private final DmMessageMapper dmMessageMapper;

    /** 会话 Mapper：用于排除群头像等聊天相关图片 */
    private final DmConversationMapper dmConversationMapper;

    /** 图片元数据 Mapper：图片空间改为按 scene 查询，避免全目录混入用户图片 */
    private final UploadFileMapper uploadFileMapper;

    /** 聊天附件独立目录（与 UserUploadController#CHAT_UPLOAD_DIR 保持一致） */
    private static final String CHAT_UPLOAD_DIR = "dm";

    /** 管理端素材场景：图片空间只展示该场景 */
    private static final String SCENE_ADMIN = "ADMIN";

    /** 允许删除素材库文件的后台角色：删除为破坏性操作，仅超管/运营可执行 */
    private static final Set<String> DELETE_ALLOWED_ROLES = Set.of("SUPER_ADMIN", "OPERATOR");

    /** 场景索引只回填一次（首次访问图片空间时触发） */
    private final AtomicBoolean sceneIndexReady = new AtomicBoolean(false);

    /** 图片白名单：扩展名 + Content-Type 双重校验（禁止 SVG，防同源存储型 XSS） */
    private static final Set<String> ALLOWED_EXT = new HashSet<>(
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp"));
    private static final Set<String> ALLOWED_CT = new HashSet<>(
            Arrays.asList("image/png", "image/jpg", "image/jpeg", "image/gif", "image/webp"));

    /** 3D 模型白名单：仅校验扩展名（模型文件 Content-Type 多为 application/octet-stream） */
    private static final Set<String> ALLOWED_MODEL_EXT = new HashSet<>(
            Arrays.asList("glb", "gltf", "fbx", "bin", "png", "jpg", "jpeg", "webp", "ktx2", "hdr", "zip"));

    /** 3D 模型独立子目录，避免与图片素材混在一起 */
    private static final String MODEL_UPLOAD_DIR = "models";

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

        // 记录图片场景：图片空间只展示 ADMIN
        try {
            adminUploadGroupService.recordUpload(url, groupId, SCENE_ADMIN, size);
        } catch (Exception e) {
            log.warn("[upload] 记录图片元数据失败：url={}, groupId={}", url, groupId, e);
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
     * 上传 3D 模型文件（GLB / GLTF / FBX 及贴图、bin 等附属文件）。
     * <p>
     * POST /api/admin/upload/model
     * multipart/form-data: file=@home.glb
     * <p>
     * 文件落到 uploads/models/yyyy/MM/dd/ 下，返回相对 URL 供模型登记使用。
     * 扩展名使用 UUID 重写，避免同名覆盖与路径穿越。
     */
    @PostMapping("/model")
    @Operation(summary = "上传 3D 模型文件")
    public Result<UploadResult> uploadModel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.badRequest("文件为空");
        }

        String original = file.getOriginalFilename();
        String ext = extractExt(original);
        if (ext == null || !ALLOWED_MODEL_EXT.contains(ext.toLowerCase())) {
            return Result.badRequest("不支持的文件类型，仅允许 GLB/GLTF/FBX/BIN/贴图");
        }
        ext = ext.toLowerCase();

        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = uploadPath.resolve(MODEL_UPLOAD_DIR).resolve(datePart).resolve(filename).normalize();
        // 防路径穿越：必须仍在 uploadPath 之下
        if (!target.startsWith(uploadPath)) {
            return Result.badRequest("非法文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[upload] 写入模型文件失败：{}", target, e);
            return Result.error("文件写入失败");
        }

        long size = file.getSize();
        String url = urlPrefix + MODEL_UPLOAD_DIR + "/" + datePart + "/" + filename;

        UploadResult result = new UploadResult();
        result.setUrl(url);
        result.setFilename(filename);
        result.setOriginalName(original);
        result.setSize(size);
        result.setContentType(file.getContentType());
        return Result.success(result);
    }

    /**
     * 分页查询已上传图片（图片空间，仅 scene=ADMIN）。
     * <p>
     * GET /api/admin/upload/list?page=1&size=24&groupId=12
     * <p>
     * groupId 传参语义：省略 / 负数 = 全部；0 = 未分组；正数 = 指定分组。
     * <p>
     * 图片元数据来自 mo_upload_file：首次访问时会把历史文件按场景回填，
     * 后续查询不再全量扫描上传目录；聊天/头像/社区等用户图片不会混入素材库。
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
        // 历史图片首次访问时回填 scene，之后列表只查元数据表
        ensureSceneIndex();

        Page<UploadFileEntity> pageObj = uploadFileMapper.selectPage(
                new Page<>(page, size), adminSceneWrapper(groupId));
        List<UploadedFile> records = new ArrayList<>(pageObj.getRecords().size());
        for (UploadFileEntity mapping : pageObj.getRecords()) {
            UploadedFile item = toUploadedFile(mapping);
            if (item == null) {
                // 元数据存在但文件已丢失：清理脏数据，避免长期展示失效图片
                try {
                    uploadFileMapper.deleteById(mapping.getId());
                } catch (Exception e) {
                    log.warn("[upload] 清理失效图片元数据失败：url={}", mapping.getUrl(), e);
                }
                continue;
            }
            records.add(item);
        }

        // 总占用只按 ADMIN 场景+当前分组统计，size 来自元数据，不再逐个读盘
        long totalSize = 0L;
        for (UploadFileEntity mapping : uploadFileMapper.selectList(adminSceneWrapper(groupId))) {
            totalSize += mapping.getSize() == null ? 0L : mapping.getSize();
        }

        UploadPage result = new UploadPage();
        result.setRecords(records);
        result.setTotal(pageObj.getTotal());
        result.setPage(page);
        result.setSize(size);
        result.setTotalSize(totalSize);
        return Result.success(result);
    }

    /** 管理端素材查询条件：scene=ADMIN + 分组筛选 + 最新优先 */
    private LambdaQueryWrapper<UploadFileEntity> adminSceneWrapper(Long groupId) {
        LambdaQueryWrapper<UploadFileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UploadFileEntity::getScene, SCENE_ADMIN);
        if (groupId != null && groupId > 0) {
            wrapper.eq(UploadFileEntity::getGroupId, groupId);
        } else if (groupId != null && groupId == 0) {
            wrapper.isNull(UploadFileEntity::getGroupId);
        }
        wrapper.orderByDesc(UploadFileEntity::getCreateTime)
                .orderByDesc(UploadFileEntity::getId);
        return wrapper;
    }

    /** 元数据行 → 图片空间列表项；文件缺失返回 null */
    private UploadedFile toUploadedFile(UploadFileEntity mapping) {
        String url = mapping.getUrl();
        if (url == null || !url.startsWith(urlPrefix)) {
            return null;
        }
        String relative = url.substring(urlPrefix.length());
        Path file = uploadPath.resolve(relative).normalize();
        if (!file.startsWith(uploadPath) || !Files.isRegularFile(file)) {
            return null;
        }
        UploadedFile item = new UploadedFile();
        item.setUrl(url);
        item.setFilename(file.getFileName().toString());
        long size = mapping.getSize() == null ? 0L : mapping.getSize();
        item.setSize(size > 0 ? size : safeFileSize(file));
        try {
            item.setLastModified(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            item.setLastModified(0L);
        }
        item.setGroupId(mapping.getGroupId());
        return item;
    }

    private long safeFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 首次访问图片空间时回填历史文件场景：
     * - dm/ 目录或私信消息/群头像引用的图片 → DM
     * - 其余历史图片 → ADMIN（保持原有素材库可见）
     */
    private void ensureSceneIndex() {
        if (sceneIndexReady.get()) {
            return;
        }
        synchronized (this) {
            if (sceneIndexReady.get()) {
                return;
            }
            rebuildSceneIndex();
        }
    }

    /** 执行场景索引回填（同步执行，避免并发访问时列表不完整） */
    private void rebuildSceneIndex() {
        long started = System.currentTimeMillis();
        try {
            Map<String, UploadFileEntity> known = new HashMap<>();
            for (UploadFileEntity mapping : uploadFileMapper.selectList(null)) {
                if (mapping.getUrl() != null) known.put(mapping.getUrl(), mapping);
            }
            Set<String> chatPaths = chatImagePaths();
            int inserted = 0;
            if (Files.isDirectory(uploadPath)) {
                try (Stream<Path> stream = Files.walk(uploadPath, 4)) {
                    List<Path> files = stream.filter(Files::isRegularFile).toList();
                    for (Path file : files) {
                        String ext = extractExt(file.getFileName().toString());
                        if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase())) continue;
                        String relative = uploadPath.relativize(file).toString().replace('\\', '/');
                        String url = urlPrefix + relative;
                        UploadFileEntity existing = known.get(url);
                        if (existing != null) {
                            // 老映射可能没有 size，顺手补齐，保证图片空间总大小准确
                            if (existing.getSize() == null || existing.getSize() <= 0) {
                                existing.setSize(safeFileSize(file));
                                try {
                                    uploadFileMapper.updateById(existing);
                                } catch (Exception e) {
                                    log.warn("[upload] 补齐图片大小失败：url={}", url, e);
                                }
                            }
                            continue;
                        }
                        String scene = (relative.startsWith(CHAT_UPLOAD_DIR + "/")
                                || chatPaths.contains(relative)) ? "DM" : SCENE_ADMIN;
                        UploadFileEntity entity = new UploadFileEntity();
                        entity.setUrl(url);
                        entity.setScene(scene);
                        entity.setSize(safeFileSize(file));
                        try {
                            uploadFileMapper.insert(entity);
                            inserted += 1;
                        } catch (Exception e) {
                            log.warn("[upload] 回填图片场景失败：url={}", url, e);
                        }
                    }
                }
            }
            sceneIndexReady.set(true);
            log.info("[upload] 图片场景索引回填完成：inserted={}, cost={}ms",
                    inserted, System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.error("[upload] 图片场景索引回填失败，下次访问图片空间将重试", e);
        }
    }

    /** 私信消息 / 群头像引用的图片相对路径（用于历史数据场景识别） */
    private Set<String> chatImagePaths() {
        Set<String> paths = new HashSet<>();
        try {
            List<String> urls = new ArrayList<>(dmMessageMapper.selectImageContents());
            urls.addAll(dmConversationMapper.selectGroupAvatars());
            for (String url : urls) {
                if (url != null && url.startsWith(urlPrefix)) {
                    paths.add(url.substring(urlPrefix.length()));
                }
            }
        } catch (Exception e) {
            log.warn("[upload] 查询聊天图片引用失败，历史聊天图片可能被归为 ADMIN", e);
        }
        return paths;
    }

    /**
     * 删除已上传图片（仅删除本地文件，不影响其他业务）。
     * <p>
     * DELETE /api/admin/upload/image?url=/uploads/2026/08/18/abc.png
     */
    @DeleteMapping("/image")
    @Operation(summary = "删除已上传图片")
    public Result<Void> deleteImage(@RequestParam("url") String url) {
        // 2026-11 安全修复：删除属于破坏性操作，上传接口虽对全部后台角色开放，
        // 但删除必须限制为运营/超管，且只能删管理端素材库（ADMIN 场景）文件。
        // 防止客服/财务/只读等角色越权删除用户私信图片、社区图片等 C 端内容。
        String role = UserContextHolder.getRole();
        if (role == null || !DELETE_ALLOWED_ROLES.contains(role)) {
            return Result.forbidden("无权限删除素材库文件");
        }
        if (url == null || url.isBlank()) {
            return Result.badRequest("url 不能为空");
        }
        // 仅允许删除 urlPrefix 范围内的文件，防止任意路径删除
        if (!url.startsWith(urlPrefix)) {
            return Result.badRequest("非法 URL");
        }
        String normalizedUrl = url.trim();
        UploadFileEntity mapping = uploadFileMapper.selectOne(
                new LambdaQueryWrapper<UploadFileEntity>()
                        .eq(UploadFileEntity::getUrl, normalizedUrl)
                        .eq(UploadFileEntity::getScene, SCENE_ADMIN));
        if (mapping == null) {
            // 非管理端素材（用户图片/聊天图片/头像等）一律拒绝，避免越权删除用户内容
            return Result.forbidden("仅允许删除管理端素材库文件");
        }
        String relative = normalizedUrl.substring(urlPrefix.length());
        // 二道防线：即使历史上被误标为 ADMIN，私信目录 dm/ 下的文件也绝不允许后台删除
        if (relative.startsWith(CHAT_UPLOAD_DIR + "/")) {
            return Result.forbidden("仅允许删除管理端素材库文件");
        }
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
     * 2026-11 安全修复：不再覆盖 svg，避免通过伪造 Content-Type 绕过扩展名白名单。
     */
    private String extractExtFromContentType(String contentType) {
        if (contentType == null) return null;
        switch (contentType.toLowerCase()) {
            case "image/png":         return "png";
            case "image/jpeg":
            case "image/jpg":         return "jpg";
            case "image/gif":         return "gif";
            case "image/webp":        return "webp";
            case "video/mp4":         return "mp4";
            case "video/quicktime":   return "mov";
            case "video/webm":        return "webm";
            default:                  return null;
        }
    }
}
package com.moyuyo.api.controller;

import com.moyuyo.common.dto.admin.UploadResult;
import com.moyuyo.common.Result;
import com.moyuyo.common.security.MediaUrlSigner;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.admin.AdminUploadGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class UserUploadController {

    /** 上传元数据服务：记录图片场景（图片空间只展示 ADMIN） */
    private final AdminUploadGroupService adminUploadGroupService;

    /** 私信媒体签名器：dm 目录的返回 URL 必须带签名，否则前端上传后立即回显会被 403 */
    private final MediaUrlSigner mediaUrlSigner;

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

    /** 语音白名单：覆盖 H5（webm/ogg）与 APP（mp3/m4a/aac）录音格式 */
    private static final Set<String> ALLOWED_VOICE_EXT = new HashSet<>(
            Arrays.asList("mp3", "m4a", "aac", "wav", "ogg", "webm"));
    private static final Set<String> ALLOWED_VOICE_CT = new HashSet<>(
            Arrays.asList("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/aac",
                    "audio/wav", "audio/x-wav", "audio/ogg", "audio/webm"));

    /** 私信文件白名单：仅常见文档/压缩包，避免被当成任意文件中转站 */
    private static final Set<String> ALLOWED_FILE_EXT = new HashSet<>(
            Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                    "txt", "md", "csv", "zip", "rar", "7z"));

    /** 各类型单文件大小上限：配合 application.yml 的全局 120MB 兜底，按业务类型进一步收紧 */
    private static final long IMAGE_MAX_SIZE = 10L * 1024 * 1024;   // 图片 10MB
    private static final long VIDEO_MAX_SIZE = 100L * 1024 * 1024;  // 视频 100MB
    private static final long VOICE_MAX_SIZE = 15L * 1024 * 1024;   // 语音 15MB
    private static final long FILE_MAX_SIZE = 30L * 1024 * 1024;    // 文档/压缩包 30MB

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

    /** 私信聊天附件独立目录：与管理端图片空间隔离，避免聊天图片混入商品素材库 */
    private static final String CHAT_UPLOAD_DIR = "dm";

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
    public Result<UploadResult> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String scene) {
        return uploadImageInternal(file, "", scene);
    }

    /**
     * 上传聊天图片（私信/群聊专用）。
     * <p>
     * POST /api/v1/file/upload/chat-image
     * <p>
     * 与普通用户图片共用校验逻辑，但落盘到 /uploads/dm/ 下，
     * 管理端图片空间会跳过该目录，保证聊天图片不进入商品素材库。
     */
    @PostMapping("/chat-image")
    @Operation(summary = "用户端上传聊天图片")
    public Result<UploadResult> uploadChatImage(@RequestParam("file") MultipartFile file) {
        return uploadImageInternal(file, CHAT_UPLOAD_DIR, "DM");
    }

    /** 图片上传通用实现：subDir 为空时存根目录，否则存到该子目录下 */
    private Result<UploadResult> uploadImageInternal(MultipartFile file, String subDir, String scene) {
        // 显式登录态校验：UserContextHolder.getUserId() 未登录时返回 null（不会抛异常），
        // 因此这里必须自行判空返回 401，不能依赖全局异常处理器兜底
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        if (file == null || file.isEmpty()) {
            return Result.badRequest("文件为空");
        }
        // 按类型的大小上限校验：即使全局 multipart 放宽，也不允许图片超出 10MB
        if (file.getSize() > IMAGE_MAX_SIZE) {
            return Result.badRequest("图片不能超过 10MB");
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
        // 文件头（魔数）识别真实类型：允许扩展名与真实格式不一致（如 PNG 改名 .jpg），
        // 只要内容属于 PNG/JPEG/GIF/WebP 即接受，并用真实类型作为落盘扩展名
        String detectedExt = detectImageExt(file);
        if (detectedExt == null) {
            return Result.badRequest("图片内容非法或已损坏");
        }
        ext = detectedExt;

        // 构造日期分目录路径：yyyy/MM/dd
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();

        Path basePath = subDir.isEmpty() ? uploadPath : uploadPath.resolve(subDir);
        Path target = basePath.resolve(datePart).resolve(filename).normalize();
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
        String url = urlPrefix + (subDir.isEmpty() ? "" : subDir + "/") + datePart + "/" + filename;

        UploadResult result = new UploadResult();
        // 私信目录 URL 追加签名（公开图片路径 sign 会原样返回）
        result.setUrl(mediaUrlSigner.sign(url));
        result.setFilename(filename);
        result.setOriginalName(original);
        result.setSize(size);
        result.setContentType(ct);
        // 记录图片场景：图片空间只展示 ADMIN，聊天/头像/社区图片不会混入素材库
        try {
            adminUploadGroupService.recordUpload(url, null, scene, size);
        } catch (Exception e) {
            log.warn("[user-upload] 记录上传场景失败：url={}, scene={}", url, scene, e);
        }
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
            // 批量图片默认 USER 场景（社区/反馈等由各自页面按需改用单张接口带 scene）
            Result<UploadResult> r = uploadImageInternal(f, "", "USER");
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
     * 根据文件头（魔数）识别真实图片类型，返回 png/jpg/gif/webp；无法识别返回 null。
     * 允许扩展名与真实格式不一致（如 PNG 改名 .jpg），只要内容属于支持格式即通过，
     * 上层会用返回的真实类型作为落盘扩展名，避免"扩展名与内容不符导致浏览器无法渲染"。
     */
    private String detectImageExt(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (var in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            log.warn("[user-upload] 读取文件头失败：{}", e.getMessage());
            return null;
        }
        if (read < 4) {
            return null;
        }
        // PNG: 89 50 4E 47
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return "png";
        }
        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // GIF: GIF8
        if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return "gif";
        }
        // WebP: RIFF....WEBP
        if (read >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /**
     * 视频文件头校验：
     * - MP4/MOV：ISO BMFF 的 {@code ftyp} 盒子，或首盒为 moov/mdat/wide/free（部分 MOV 无 ftyp）
     * - WebM：EBML 魔数 1A 45 DF A3
     */
    private boolean matchesVideoContent(MultipartFile file, String ext) {
        byte[] h = readHeader(file, 16);
        if (h.length < 8) {
            return false;
        }
        boolean webm = (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45
                && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3;
        if ("webm".equalsIgnoreCase(ext)) {
            return webm;
        }
        boolean isoTyp = h.length >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        boolean quickTimeBox = (h[4] == 'm' && h[5] == 'o' && h[6] == 'o' && h[7] == 'v')
                || (h[4] == 'm' && h[5] == 'd' && h[6] == 'a' && h[7] == 't')
                || (h[4] == 'w' && h[5] == 'i' && h[6] == 'd' && h[7] == 'e')
                || (h[4] == 'f' && h[5] == 'r' && h[6] == 'e' && h[7] == 'e');
        return isoTyp || quickTimeBox;
    }

    /**
     * 语音文件头校验（按扩展名匹配对应魔数）：
     * MP3(ID3/帧同步) / M4A(ftyp) / AAC(ADTS) / WAV(RIFF+WAVE) / OGG(OggS) / WebM(EBML)
     */
    private boolean matchesVoiceContent(MultipartFile file, String ext) {
        byte[] h = readHeader(file, 12);
        if (h.length < 4) {
            return false;
        }
        switch (ext.toLowerCase()) {
            case "mp3":
                return (h.length >= 3 && h[0] == 'I' && h[1] == 'D' && h[2] == '3')
                        || ((h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0);
            case "m4a":
                return h.length >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
            case "aac":
                return (h[0] & 0xFF) == 0xFF && (h[1] & 0xF0) == 0xF0;
            case "wav":
                return h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                        && h[8] == 'W' && h[9] == 'A' && h[10] == 'V' && h[11] == 'E';
            case "ogg":
                return h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S';
            case "webm":
                return (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45
                        && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3;
            default:
                return false;
        }
    }

    /**
     * 文档/压缩包文件头校验：
     * - PDF: %PDF
     * - ZIP/OOXML(docx/xlsx/pptx): PK 头
     * - 旧版 Office(doc/xls/ppt): OLE2 复合文档头
     * - RAR / 7Z: 对应魔数
     * - TXT/MD/CSV: 前 512 字节不得包含 NUL（否则视为二进制伪装）
     */
    private boolean matchesFileContent(MultipartFile file, String ext) {
        String lower = ext.toLowerCase();
        if (lower.equals("txt") || lower.equals("md") || lower.equals("csv")) {
            return !containsNul(file);
        }
        byte[] h = readHeader(file, 8);
        if (h.length < 4) {
            return false;
        }
        switch (lower) {
            case "pdf":
                return h.length >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case "zip":
            case "docx":
            case "xlsx":
            case "pptx":
                return h[0] == 'P' && h[1] == 'K' && (h[2] == 3 || h[2] == 5 || h[2] == 7) && h[3] == 4;
            case "doc":
            case "xls":
            case "ppt":
                return h.length >= 8 && (h[0] & 0xFF) == 0xD0 && (h[1] & 0xFF) == 0xCF
                        && (h[2] & 0xFF) == 0x11 && (h[3] & 0xFF) == 0xE0;
            case "rar":
                return h[0] == 'R' && h[1] == 'a' && h[2] == 'r' && h[3] == '!';
            case "7z":
                return h.length >= 6 && (h[0] & 0xFF) == 0x37 && (h[1] & 0xFF) == 0x7A
                        && (h[2] & 0xFF) == 0xBC && (h[3] & 0xFF) == 0xAF
                        && (h[4] & 0xFF) == 0x27 && (h[5] & 0xFF) == 0x1C;
            default:
                return false;
        }
    }

    /** 读取文件头（最多 max 字节）；读取失败返回空数组，由调用方按不合法处理 */
    private byte[] readHeader(MultipartFile file, int max) {
        byte[] buffer = new byte[max];
        try (var in = file.getInputStream()) {
            int read = in.readNBytes(buffer, 0, max);
            return read <= 0 ? new byte[0] : Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            log.warn("[user-upload] 读取文件头失败：{}", e.getMessage());
            return new byte[0];
        }
    }

    /** 前 512 字节是否包含 NUL（文本文件不应出现） */
    private boolean containsNul(MultipartFile file) {
        byte[] buffer = new byte[512];
        try (var in = file.getInputStream()) {
            int read = in.readNBytes(buffer, 0, buffer.length);
            for (int i = 0; i < read; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
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
        // 按类型的大小上限校验：视频单独放宽到 100MB
        if (file.getSize() > VIDEO_MAX_SIZE) {
            return Result.badRequest("视频不能超过 100MB");
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
        // 文件头校验：防止把任意二进制改名为 .mp4/.mov 上传
        if (!matchesVideoContent(file, ext)) {
            return Result.badRequest("视频内容非法或已损坏");
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

    /**
     * 上传语音（私信语音消息）。
     * <p>
     * POST /api/v1/file/upload/voice
     * multipart/form-data: file=@xxx.mp3
     */
    @PostMapping("/voice")
    @Operation(summary = "用户端上传语音")
    public Result<UploadResult> uploadVoice(@RequestParam("file") MultipartFile file) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (file == null || file.isEmpty()) {
            return Result.badRequest("文件为空");
        }
        // 按类型的大小上限校验：语音 15MB
        if (file.getSize() > VOICE_MAX_SIZE) {
            return Result.badRequest("语音不能超过 15MB");
        }

        String original = file.getOriginalFilename();
        String ct = file.getContentType();
        String ext = extractExt(original);
        if (ext == null) {
            ext = extractExtFromContentType(ct);
        }
        // H5 MediaRecorder 生成的 blob 常常没有扩展名，按 Content-Type 兜底成 webm
        if (ext == null && ct != null && ct.toLowerCase().startsWith("audio/")) {
            ext = ct.toLowerCase().contains("ogg") ? "ogg" : "webm";
        }
        if (ext == null || !ALLOWED_VOICE_EXT.contains(ext.toLowerCase())) {
            return Result.badRequest("不支持的语音格式，仅允许 MP3/M4A/AAC/WAV/OGG/WebM");
        }
        if (ct != null && !ct.isBlank()) {
            // MediaRecorder 会带 codecs 参数（audio/webm;codecs=opus），比较前先去掉参数
            String baseCt = ct.split(";")[0].trim().toLowerCase();
            if (!ALLOWED_VOICE_CT.contains(baseCt)) {
                return Result.badRequest("语音 Content-Type 不匹配");
            }
        }
        // 文件头校验：防止任意内容伪装成音频
        if (!matchesVoiceContent(file, ext)) {
            return Result.badRequest("语音内容非法或已损坏");
        }

        return store(file, ext, original, ct, userId, "语音", CHAT_UPLOAD_DIR);
    }

    /**
     * 上传文件（私信文件消息）。
     * <p>
     * POST /api/v1/file/upload/file
     * multipart/form-data: file=@xxx.pdf
     */
    @PostMapping("/file")
    @Operation(summary = "用户端上传文件")
    public Result<UploadResult> uploadFile(@RequestParam("file") MultipartFile file) {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        if (file == null || file.isEmpty()) {
            return Result.badRequest("文件为空");
        }
        // 按类型的大小上限校验：文档/压缩包 30MB
        if (file.getSize() > FILE_MAX_SIZE) {
            return Result.badRequest("文件不能超过 30MB");
        }

        String original = file.getOriginalFilename();
        String ct = file.getContentType();
        String ext = extractExt(original);
        if (ext == null || !ALLOWED_FILE_EXT.contains(ext.toLowerCase())) {
            return Result.badRequest("不支持的文件类型，仅允许 PDF/Word/Excel/PPT/TXT/MD/CSV/ZIP/RAR/7Z");
        }
        // 文件类型主要依赖扩展名白名单；浏览器/APP 常传 application/octet-stream，不做强 CT 校验
        // 但必须校验文件头，避免把任意二进制改名为 .pdf/.zip 后当作文档中转
        if (!matchesFileContent(file, ext)) {
            return Result.badRequest("文件内容与扩展名不匹配");
        }

        return store(file, ext, original, ct, userId, "文件", CHAT_UPLOAD_DIR);
    }

    /**
     * 统一落盘：日期分目录 + UUID 文件名，返回相对 URL。
     * <p>
     * subDir 为 dm 时落到受访问控制的私信目录（静态资源需签名才能读取），
     * 为空时落到公开上传目录（商品/社区等公开图片场景）。
     */
    private Result<UploadResult> store(MultipartFile file, String ext, String original,
                                       String ct, Long userId, String label, String subDir) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();

        Path basePath = subDir == null || subDir.isEmpty() ? uploadPath : uploadPath.resolve(subDir);
        Path target = basePath.resolve(datePart).resolve(filename).normalize();
        if (!target.startsWith(uploadPath)) {
            return Result.badRequest("非法文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[user-upload] 写入{}失败：{}", label, target, e);
            return Result.error(label + "写入失败");
        }

        long size = file.getSize();
        String url = urlPrefix + (subDir == null || subDir.isEmpty() ? "" : subDir + "/")
                + datePart + "/" + filename;

        UploadResult result = new UploadResult();
        // 私信目录 URL 追加签名（公开图片路径 sign 会原样返回）
        result.setUrl(mediaUrlSigner.sign(url));
        result.setFilename(filename);
        result.setOriginalName(original);
        result.setSize(size);
        result.setContentType(ct);
        log.info("[user-upload] userId={} uploaded {} {} bytes -> {}", userId, label, size, url);
        return Result.success(result);
    }
}
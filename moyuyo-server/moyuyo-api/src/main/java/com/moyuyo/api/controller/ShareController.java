package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 分享商品埋点接口。
 * 前端 share-product.vue 在用户选择具体分享渠道时调用，
 * 用于触发任务中心"分享 1 个商品"每日任务。
 */
@Slf4j
@Tag(name = "分享中心")
@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
public class ShareController {

    private final MissionService missionService;
    private final RestTemplate restTemplate;

    @Operation(summary = "分享商品埋点")
    @PostMapping("/product")
    public Result<Map<String, Object>> shareProduct() {
        Long userId = UserContextHolder.getUserId();
        // 触发"分享 1 个商品"每日任务 +1
        missionService.incrementByKeyword(userId, "DAILY", "分享", 1);
        log.info("[share] product shared: userId={}", userId);
        return Result.success(Map.of("ok", true));
    }

    /**
     * 分享链接二维码生成：透传公共 QR API 返回 PNG 字节流。
     * 入参 text 由前端构造（如商品详情 URL + 推荐人 ID），后端仅做代理 + URL 编码。
     * 公共 QR API：https://api.qrserver.com/v1/create-qr-code/，返回 image/png。
     * 注：该路径未加入 JwtAuthFilter 白名单，匿名访问即可，符合"未登录用户也能扫码看商品"的预期。
     */
    @Operation(summary = "分享链接二维码（公开）")
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQr(
            @RequestParam("text") String text,
            @RequestParam(value = "size", defaultValue = "240") int size) {
        // 兜底：文本过长截断，避免下游 URL 过长导致 414/生成失败
        if (text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (text.length() > 500) {
            text = text.substring(0, 500);
        }
        // 限制像素范围：120~600，UI 缩放更平滑
        int clampedSize = Math.max(120, Math.min(600, size));
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String upstream = "https://api.qrserver.com/v1/create-qr-code/?data="
                    + encoded + "&size=" + clampedSize + "x" + clampedSize + "&margin=1";
            byte[] png = restTemplate.getForObject(upstream, byte[].class);
            if (png == null || png.length == 0) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            // 浏览器/小程序可缓存 1 小时（同 URL 文本一致则命中）
            headers.setCacheControl("public, max-age=3600");
            return new ResponseEntity<>(png, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.warn("[share-qr] upstream call failed: text.len={}, size={}", text.length(), clampedSize, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
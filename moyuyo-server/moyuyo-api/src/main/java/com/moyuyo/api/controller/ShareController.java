package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "分享商品埋点")
    @PostMapping("/product")
    public Result<Map<String, Object>> shareProduct() {
        Long userId = UserContextHolder.getUserId();
        // 触发"分享 1 个商品"每日任务 +1
        missionService.incrementByKeyword(userId, "DAILY", "分享", 1);
        log.info("[share] product shared: userId={}", userId);
        return Result.success(Map.of("ok", true));
    }
}
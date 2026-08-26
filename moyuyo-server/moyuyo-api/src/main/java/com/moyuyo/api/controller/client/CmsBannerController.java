package com.moyuyo.api.controller.client;

import com.moyuyo.common.Result;
import com.moyuyo.dao.admin.entity.CmsContentEntity;
import com.moyuyo.dao.admin.mapper.CmsContentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端公开 Banner 接口
 * <p>
 * 供 APP 首页拉取 CMS 后台配置的营销横幅。无须鉴权（已在 JwtAuthFilter 白名单）。
 * 仅返回 ACTIVE 状态、当前时间在投放区间内的记录，按 sortOrder 升序。
 */
@Tag(name = "C 端 - CMS Banner")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cms/banners")
public class CmsBannerController {

    private final CmsContentMapper cmsContentMapper;

    @Operation(summary = "首页 Banner 列表（公开）")
    @GetMapping("")
    public Result<List<Map<String, Object>>> list() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<CmsContentEntity> all = cmsContentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CmsContentEntity>()
                    .eq(CmsContentEntity::getType, "BANNER")
                    .eq(CmsContentEntity::getStatus, "ACTIVE")
                    .and(w -> w.isNull(CmsContentEntity::getStartTime).or().le(CmsContentEntity::getStartTime, now))
                    .and(w -> w.isNull(CmsContentEntity::getEndTime).or().ge(CmsContentEntity::getEndTime, now))
                    .orderByAsc(CmsContentEntity::getSortOrder)
            );
            List<Map<String, Object>> result = new ArrayList<>();
            for (CmsContentEntity e : all) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.getId());
                item.put("title", e.getTitle());
                item.put("description", e.getContent());
                item.put("imageUrl", e.getImageUrl());
                item.put("linkUrl", e.getLinkUrl());
                item.put("location", e.getLocation());
                item.put("sortOrder", e.getSortOrder());
                item.put("tag", extractTag(e.getTitle(), e.getContent()));
                result.add(item);
            }
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("查询Banner失败: " + e.getMessage());
        }
    }

    /**
     * 提取 Banner 标签，例如 title 包含"LIMITED/HOT/限时"时作为顶部小标签展示
     */
    private String extractTag(String title, String content) {
        if (title != null) {
            String t = title.toUpperCase();
            if (t.contains("LIMITED")) return "LIMITED";
            if (t.contains("HOT")) return "HOT";
            if (t.contains("NEW")) return "NEW";
            if (t.contains("限时")) return "限时";
        }
        if (content != null && content.toUpperCase().contains("LIMITED")) return "LIMITED";
        return null;
    }
}
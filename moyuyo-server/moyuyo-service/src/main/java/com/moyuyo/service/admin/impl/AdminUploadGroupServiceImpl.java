package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyuyo.dao.admin.entity.UploadFileEntity;
import com.moyuyo.dao.admin.entity.UploadGroupEntity;
import com.moyuyo.dao.admin.mapper.UploadFileMapper;
import com.moyuyo.dao.admin.mapper.UploadGroupMapper;
import com.moyuyo.service.admin.AdminUploadGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片空间分组服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUploadGroupServiceImpl implements AdminUploadGroupService {

    /** 分组名称最大长度，与 mo_upload_group.name 定义保持一致 */
    private static final int MAX_NAME_LENGTH = 64;
    /** 图片 URL 最大长度，与 mo_upload_file.url 定义保持一致 */
    private static final int MAX_URL_LENGTH = 512;
    /** 仅允许操作上传目录下的图片，防止越权写入任意 URL */
    private static final String UPLOAD_PREFIX = "/uploads/";
    /** 管理端素材场景：图片空间只展示该场景 */
    private static final String SCENE_ADMIN = "ADMIN";

    private final UploadGroupMapper groupMapper;
    private final UploadFileMapper fileMapper;

    @Override
    public List<Map<String, Object>> listGroups() {
        List<UploadGroupEntity> groups = groupMapper.selectList(
                Wrappers.<UploadGroupEntity>lambdaQuery()
                        .orderByAsc(UploadGroupEntity::getSortOrder)
                        .orderByAsc(UploadGroupEntity::getId));

        // 分组图片数量来自映射表；删除图片时映射会同步清理，统计不会持续虚高
        Map<Long, Long> counts = fileMapper.selectList(
                        Wrappers.<UploadFileEntity>lambdaQuery()
                                .eq(UploadFileEntity::getScene, SCENE_ADMIN)
                                .isNotNull(UploadFileEntity::getGroupId))
                .stream()
                .collect(Collectors.groupingBy(UploadFileEntity::getGroupId, Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>(groups.size());
        for (UploadGroupEntity group : groups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", group.getId());
            item.put("name", group.getName());
            item.put("sortOrder", group.getSortOrder());
            item.put("imageCount", counts.getOrDefault(group.getId(), 0L));
            item.put("createTime", group.getCreateTime());
            result.add(item);
        }
        return result;
    }

    @Override
    public Long createGroup(String name) {
        String normalized = normalizeName(name);
        long duplicated = groupMapper.selectCount(
                Wrappers.<UploadGroupEntity>lambdaQuery().eq(UploadGroupEntity::getName, normalized));
        if (duplicated > 0) {
            throw new IllegalArgumentException("分组名称已存在");
        }

        UploadGroupEntity entity = new UploadGroupEntity();
        entity.setName(normalized);
        entity.setSortOrder(nextSortOrder());
        groupMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void renameGroup(Long id, String name) {
        if (id == null) {
            throw new IllegalArgumentException("分组ID不能为空");
        }
        UploadGroupEntity existing = groupMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("分组不存在");
        }

        String normalized = normalizeName(name);
        long duplicated = groupMapper.selectCount(
                Wrappers.<UploadGroupEntity>lambdaQuery()
                        .eq(UploadGroupEntity::getName, normalized)
                        .ne(UploadGroupEntity::getId, id));
        if (duplicated > 0) {
            throw new IllegalArgumentException("分组名称已存在");
        }

        existing.setName(normalized);
        groupMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("分组ID不能为空");
        }
        UploadGroupEntity existing = groupMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("分组不存在");
        }
        // 只解除归属，不删除图片文件；图片回到「未分组」仍保留在图片空间
        fileMapper.update(null,
                Wrappers.<UploadFileEntity>lambdaUpdate()
                        .eq(UploadFileEntity::getGroupId, id)
                        .set(UploadFileEntity::getGroupId, null));
        groupMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignGroup(List<String> urls, Long groupId) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("请选择要移动的图片");
        }
        // null / 0 表示移出分组
        Long targetGroupId = (groupId == null || groupId <= 0) ? null : groupId;
        if (targetGroupId != null && groupMapper.selectById(targetGroupId) == null) {
            throw new IllegalArgumentException("目标分组不存在");
        }

        Set<String> uniqueUrls = new LinkedHashSet<>();
        for (String url : urls) {
            String normalized = normalizeUrl(url);
            if (normalized != null) {
                uniqueUrls.add(normalized);
            }
        }
        if (uniqueUrls.isEmpty()) {
            throw new IllegalArgumentException("图片地址不合法");
        }

        for (String url : uniqueUrls) {
            UploadFileEntity mapping = fileMapper.selectOne(
                    Wrappers.<UploadFileEntity>lambdaQuery().eq(UploadFileEntity::getUrl, url));
            if (targetGroupId == null) {
                // 移出分组：只清空 group_id，保留场景元数据（图片继续出现在未分组）
                if (mapping != null) {
                    mapping.setGroupId(null);
                    fileMapper.updateById(mapping);
                }
                continue;
            }
            if (mapping == null) {
                UploadFileEntity created = new UploadFileEntity();
                created.setUrl(url);
                created.setGroupId(targetGroupId);
                created.setScene(SCENE_ADMIN);
                created.setSize(0L);
                fileMapper.insert(created);
            } else {
                mapping.setGroupId(targetGroupId);
                fileMapper.updateById(mapping);
            }
        }
    }

    @Override
    public Map<String, Long> groupIdByUrl() {
        List<UploadFileEntity> mappings = fileMapper.selectList(
                Wrappers.<UploadFileEntity>lambdaQuery()
                        .eq(UploadFileEntity::getScene, SCENE_ADMIN)
                        .isNotNull(UploadFileEntity::getGroupId));
        Map<String, Long> result = new HashMap<>(Math.max(16, mappings.size() * 2));
        for (UploadFileEntity mapping : mappings) {
            result.put(mapping.getUrl(), mapping.getGroupId());
        }
        return result;
    }

    @Override
    public void bindOnUpload(String url, Long groupId) {
        // 兼容旧调用：按管理端素材记录
        recordUpload(url, groupId, SCENE_ADMIN, 0L);
    }

    @Override
    public void recordUpload(String url, Long groupId, String scene, long size) {
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            return;
        }
        String normalizedScene = normalizeScene(scene);
        Long targetGroupId = (groupId == null || groupId <= 0) ? null : groupId;

        if (targetGroupId != null && groupMapper.selectById(targetGroupId) == null) {
            log.warn("[upload] 上传指定的分组不存在，按未分组处理：groupId={}, url={}",
                    targetGroupId, normalized);
            targetGroupId = null;
        }

        UploadFileEntity mapping = fileMapper.selectOne(
                Wrappers.<UploadFileEntity>lambdaQuery().eq(UploadFileEntity::getUrl, normalized));
        if (mapping == null) {
            UploadFileEntity created = new UploadFileEntity();
            created.setUrl(normalized);
            created.setScene(normalizedScene);
            created.setSize(Math.max(size, 0L));
            created.setGroupId(targetGroupId);
            fileMapper.insert(created);
        } else {
            mapping.setScene(normalizedScene);
            mapping.setSize(Math.max(size, 0L));
            // 未指定分组时保留原有分组归属
            if (targetGroupId != null) {
                mapping.setGroupId(targetGroupId);
            }
            fileMapper.updateById(mapping);
        }
    }

    /** 场景收敛：仅允许已知场景，未知值按 USER 处理 */
    private String normalizeScene(String scene) {
        if (scene == null) return "USER";
        String value = scene.trim().toUpperCase();
        return switch (value) {
            case "ADMIN", "AVATAR", "COMMUNITY", "DM", "USER" -> value;
            default -> "USER";
        };
    }

    @Override
    public void unbindOnDelete(String url) {
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            return;
        }
        fileMapper.delete(Wrappers.<UploadFileEntity>lambdaQuery()
                .eq(UploadFileEntity::getUrl, normalized));
    }

    /** 校验收敛分组名称：非空 + 长度上限 */
    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("分组名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return trimmed;
    }

    /** 新分组追加到末尾（取当前最大排序值 +1） */
    private Integer nextSortOrder() {
        UploadGroupEntity last = groupMapper.selectOne(
                Wrappers.<UploadGroupEntity>lambdaQuery()
                        .orderByDesc(UploadGroupEntity::getSortOrder)
                        .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    /** 图片 URL 校验：必须位于上传目录、长度受限、禁止路径穿越 */
    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_URL_LENGTH) {
            return null;
        }
        if (!trimmed.startsWith(UPLOAD_PREFIX) || trimmed.contains("..")) {
            return null;
        }
        return trimmed;
    }
}

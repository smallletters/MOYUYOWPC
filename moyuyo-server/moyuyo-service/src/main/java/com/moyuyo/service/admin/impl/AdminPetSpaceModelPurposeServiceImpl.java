package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.PetSpaceModelEntity;
import com.moyuyo.dao.admin.entity.PetSpaceModelPurposeEntity;
import com.moyuyo.dao.admin.mapper.PetSpaceModelMapper;
import com.moyuyo.dao.admin.mapper.PetSpaceModelPurposeMapper;
import com.moyuyo.service.admin.AdminPetSpaceModelPurposeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 管理后台 APP 宠物 3D 空间模型用途服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminPetSpaceModelPurposeServiceImpl implements AdminPetSpaceModelPurposeService {

    private final PetSpaceModelPurposeMapper purposeMapper;
    private final PetSpaceModelMapper modelMapper;

    /** 用途标识：大写字母开头，允许大写字母/数字/下划线 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    /** 允许的文件格式 */
    private static final Set<String> ALLOWED_FORMATS = Set.of("GLB", "GLTF", "FBX");

    /** APP 引擎已内置读取的用途，禁止删除，避免破坏线上 3D 空间 */
    private static final Set<String> BUILT_IN_KEYS = Set.of("SCENE", "AVATAR_IDLE", "AVATAR_WALK");

    @Override
    public List<PetSpaceModelPurposeEntity> listAll() {
        return purposeMapper.selectList(new LambdaQueryWrapper<PetSpaceModelPurposeEntity>()
                .orderByAsc(PetSpaceModelPurposeEntity::getSortOrder)
                .orderByAsc(PetSpaceModelPurposeEntity::getId));
    }

    @Override
    public List<PetSpaceModelPurposeEntity> listEnabled() {
        return purposeMapper.selectList(new LambdaQueryWrapper<PetSpaceModelPurposeEntity>()
                .eq(PetSpaceModelPurposeEntity::getEnabled, true)
                .orderByAsc(PetSpaceModelPurposeEntity::getSortOrder)
                .orderByAsc(PetSpaceModelPurposeEntity::getId));
    }

    @Override
    public void create(PetSpaceModelPurposeEntity entity) {
        validate(entity, true);
        entity.setId(null);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        purposeMapper.insert(entity);
    }

    @Override
    public void update(PetSpaceModelPurposeEntity entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("用途 ID 不能为空");
        }
        PetSpaceModelPurposeEntity existing = purposeMapper.selectById(entity.getId());
        if (existing == null) {
            throw new IllegalArgumentException("用途不存在");
        }
        // 用途标识是模型记录与 APP 读取的关联键，创建后不允许修改
        entity.setPurposeKey(existing.getPurposeKey());
        validate(entity, false);
        purposeMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PetSpaceModelPurposeEntity existing = purposeMapper.selectById(id);
        if (existing == null) {
            return;
        }
        if (BUILT_IN_KEYS.contains(existing.getPurposeKey())) {
            throw new IllegalStateException("内置用途不能删除");
        }
        long used = modelMapper.selectCount(new LambdaQueryWrapper<PetSpaceModelEntity>()
                .eq(PetSpaceModelEntity::getModelKey, existing.getPurposeKey()));
        if (used > 0) {
            throw new IllegalStateException("该用途下已有模型，请先删除模型后再删除用途");
        }
        purposeMapper.deleteById(id);
    }

    /**
     * 入参校验
     *
     * @param isCreate           是否新增；新增时校验标识唯一
     * @param entity             用途实体
     */
    private void validate(PetSpaceModelPurposeEntity entity, boolean isCreate) {
        if (entity == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        String key = entity.getPurposeKey() == null
                ? "" : entity.getPurposeKey().trim().toUpperCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("用途标识需以大写字母开头，仅允许大写字母/数字/下划线，长度 2~32");
        }
        if (isCreate) {
            long exists = purposeMapper.selectCount(new LambdaQueryWrapper<PetSpaceModelPurposeEntity>()
                    .eq(PetSpaceModelPurposeEntity::getPurposeKey, key));
            if (exists > 0) {
                throw new IllegalArgumentException("用途标识已存在");
            }
        }
        entity.setPurposeKey(key);

        String name = entity.getPurposeName() == null ? "" : entity.getPurposeName().trim();
        if (name.isEmpty() || name.length() > 64) {
            throw new IllegalArgumentException("用途名称必填且不能超过 64 个字符");
        }
        entity.setPurposeName(name);

        String formats = normalizeFormats(entity.getAllowedFormats());
        if (formats.isEmpty()) {
            throw new IllegalArgumentException("至少选择一种允许的模型格式");
        }
        entity.setAllowedFormats(formats);

        if (entity.getDescription() != null && entity.getDescription().length() > 255) {
            entity.setDescription(entity.getDescription().substring(0, 255));
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
    }

    /** 格式去重归一：仅保留 GLB/GLTF/FBX */
    private String normalizeFormats(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .filter(ALLOWED_FORMATS::contains)
                .forEach(result::add);
        return result.stream().collect(Collectors.joining(","));
    }
}

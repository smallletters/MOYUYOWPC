package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyuyo.dao.admin.entity.PetSpaceModelEntity;
import com.moyuyo.dao.admin.entity.PetSpaceModelPurposeEntity;
import com.moyuyo.dao.admin.mapper.PetSpaceModelMapper;
import com.moyuyo.dao.admin.mapper.PetSpaceModelPurposeMapper;
import com.moyuyo.service.admin.AdminPetSpaceModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台 APP 宠物 3D 空间模型服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminPetSpaceModelServiceImpl implements AdminPetSpaceModelService {

    private final PetSpaceModelMapper petSpaceModelMapper;
    private final PetSpaceModelPurposeMapper petSpaceModelPurposeMapper;

    /** 允许的模型文件格式 */
    private static final Set<String> ALLOWED_FORMATS = Set.of("GLB", "GLTF", "FBX");

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    @Override
    public List<PetSpaceModelEntity> list(String modelKey, String status) {
        LambdaQueryWrapper<PetSpaceModelEntity> wrapper = new LambdaQueryWrapper<>();
        if (modelKey != null && !modelKey.isBlank()) {
            wrapper.eq(PetSpaceModelEntity::getModelKey, modelKey.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PetSpaceModelEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(PetSpaceModelEntity::getUpdateTime)
                .orderByDesc(PetSpaceModelEntity::getId);
        return petSpaceModelMapper.selectList(wrapper);
    }

    @Override
    public void create(PetSpaceModelEntity entity) {
        validate(entity, true);
        // 新增一律停用，避免误上线；由运营确认后通过「启用」切换
        entity.setId(null);
        entity.setStatus(STATUS_DISABLED);
        petSpaceModelMapper.insert(entity);
    }

    @Override
    public void update(PetSpaceModelEntity entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        PetSpaceModelEntity existing = petSpaceModelMapper.selectById(entity.getId());
        if (existing == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        // 用途决定 APP 读取位置，编辑时以数据库中的用途为准；状态只能通过启用接口切换
        entity.setModelKey(existing.getModelKey());
        validate(entity, false);
        entity.setStatus(existing.getStatus());
        petSpaceModelMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        PetSpaceModelEntity entity = petSpaceModelMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        PetSpaceModelPurposeEntity purpose = findPurpose(entity.getModelKey());
        if (purpose == null) {
            throw new IllegalArgumentException("模型用途不存在");
        }
        if (!Boolean.TRUE.equals(purpose.getEnabled())) {
            throw new IllegalStateException("模型用途已停用，请先启用该用途");
        }
        // 同一用途只保留一个启用版本，先批量停用其它版本
        petSpaceModelMapper.update(null, new LambdaUpdateWrapper<PetSpaceModelEntity>()
                .eq(PetSpaceModelEntity::getModelKey, entity.getModelKey())
                .ne(PetSpaceModelEntity::getId, id)
                .set(PetSpaceModelEntity::getStatus, STATUS_DISABLED));
        entity.setStatus(STATUS_ENABLED);
        petSpaceModelMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        PetSpaceModelEntity entity = petSpaceModelMapper.selectById(id);
        if (entity == null) {
            return;
        }
        if (STATUS_ENABLED.equals(entity.getStatus())) {
            throw new IllegalStateException("启用中的模型不能删除，请先启用其它版本");
        }
        petSpaceModelMapper.deleteById(id);
    }

    @Override
    public List<PetSpaceModelEntity> listEnabled() {
        // 只返回「模型启用 + 用途启用」的配置，避免用途停用后 APP 仍继续使用
        List<String> enabledKeys = petSpaceModelPurposeMapper.selectList(
                        new LambdaQueryWrapper<PetSpaceModelPurposeEntity>()
                                .eq(PetSpaceModelPurposeEntity::getEnabled, true))
                .stream()
                .map(PetSpaceModelPurposeEntity::getPurposeKey)
                .collect(Collectors.toList());
        if (enabledKeys.isEmpty()) {
            return List.of();
        }
        return petSpaceModelMapper.selectList(new LambdaQueryWrapper<PetSpaceModelEntity>()
                .eq(PetSpaceModelEntity::getStatus, STATUS_ENABLED)
                .in(PetSpaceModelEntity::getModelKey, enabledKeys)
                .orderByAsc(PetSpaceModelEntity::getModelKey));
    }

    /**
     * 入参校验：用途 / 名称 / 文件地址 / 格式
     *
     * @param requirePurposeEnabled 新增/启用时要求用途处于启用状态；编辑历史模型时只校验用途存在
     */
    private void validate(PetSpaceModelEntity entity, boolean requirePurposeEnabled) {
        if (entity == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        String modelKey = entity.getModelKey() == null ? "" : entity.getModelKey().trim().toUpperCase(Locale.ROOT);
        PetSpaceModelPurposeEntity purpose = findPurpose(modelKey);
        if (purpose == null) {
            throw new IllegalArgumentException("模型用途不存在，请先在用途管理中创建");
        }
        if (requirePurposeEnabled && !Boolean.TRUE.equals(purpose.getEnabled())) {
            throw new IllegalArgumentException("模型用途已停用，请先启用该用途");
        }
        entity.setModelKey(modelKey);

        String displayName = entity.getDisplayName() == null ? "" : entity.getDisplayName().trim();
        if (displayName.isEmpty() || displayName.length() > 100) {
            throw new IllegalArgumentException("模型名称必填且不能超过 100 个字符");
        }
        entity.setDisplayName(displayName);

        String fileUrl = entity.getFileUrl() == null ? "" : entity.getFileUrl().trim();
        // 仅允许站内上传路径或 http(s) 绝对地址，避免 javascript: / data: 等危险协议
        if (!(fileUrl.startsWith("/uploads/") || fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("模型文件地址必须以 /uploads/ 或 http(s):// 开头");
        }
        if (fileUrl.length() > 512) {
            throw new IllegalArgumentException("模型文件地址不能超过 512 个字符");
        }
        entity.setFileUrl(fileUrl);

        String allowedFormats = purpose.getAllowedFormats() == null ? "" : purpose.getAllowedFormats();
        List<String> formats = Arrays.stream(allowedFormats.split(","))
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .filter(ALLOWED_FORMATS::contains)
                .collect(Collectors.toList());
        String format = entity.getFileFormat() == null
                ? "" : entity.getFileFormat().trim().toUpperCase(Locale.ROOT);
        if (format.isEmpty() && !formats.isEmpty()) {
            format = formats.get(0);
        }
        if (!ALLOWED_FORMATS.contains(format)) {
            throw new IllegalArgumentException("仅支持 GLB / GLTF / FBX 格式");
        }
        if (formats.isEmpty() || !formats.contains(format)) {
            throw new IllegalArgumentException("该用途仅支持 " + String.join(" / ", formats) + " 格式");
        }
        entity.setFileFormat(format);

        if (entity.getFileSize() == null || entity.getFileSize() < 0) {
            entity.setFileSize(0L);
        }
        if (entity.getRemark() != null && entity.getRemark().length() > 500) {
            entity.setRemark(entity.getRemark().substring(0, 500));
        }
    }

    /** 按用途标识查询用途 */
    private PetSpaceModelPurposeEntity findPurpose(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return null;
        }
        return petSpaceModelPurposeMapper.selectOne(new LambdaQueryWrapper<PetSpaceModelPurposeEntity>()
                .eq(PetSpaceModelPurposeEntity::getPurposeKey, modelKey)
                .last("LIMIT 1"));
    }
}

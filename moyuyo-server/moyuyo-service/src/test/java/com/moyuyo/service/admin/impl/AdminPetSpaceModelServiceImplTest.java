package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyuyo.dao.admin.entity.PetSpaceModelEntity;
import com.moyuyo.dao.admin.entity.PetSpaceModelPurposeEntity;
import com.moyuyo.dao.admin.mapper.PetSpaceModelMapper;
import com.moyuyo.dao.admin.mapper.PetSpaceModelPurposeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminPetSpaceModelServiceImpl 单元测试
 * 覆盖：新增默认停用、启用切换、启用中禁止删除、编辑时锁定用途/状态、用途与格式校验
 */
@ExtendWith(MockitoExtension.class)
class AdminPetSpaceModelServiceImplTest {

    @Mock
    private PetSpaceModelMapper petSpaceModelMapper;

    @Mock
    private PetSpaceModelPurposeMapper petSpaceModelPurposeMapper;

    @InjectMocks
    private AdminPetSpaceModelServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据，避免纯 Mockito 单测触发 "lambda cache" 异常 */
    @BeforeAll
    static void initMybatisTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetSpaceModelEntity.class);
        TableInfoHelper.initTableInfo(assistant, PetSpaceModelPurposeEntity.class);
    }

    private PetSpaceModelEntity buildValidEntity(Long id, String modelKey, String status) {
        PetSpaceModelEntity entity = new PetSpaceModelEntity();
        entity.setId(id);
        entity.setModelKey(modelKey);
        entity.setDisplayName("测试模型");
        entity.setFileUrl("/uploads/models/2026/11/04/demo.glb");
        entity.setFileFormat("GLB");
        entity.setFileSize(1024L);
        entity.setStatus(status);
        return entity;
    }

    /** 模拟模型用途存在（默认启用） */
    private void stubPurpose(String key, String allowedFormats) {
        PetSpaceModelPurposeEntity purpose = new PetSpaceModelPurposeEntity();
        purpose.setPurposeKey(key);
        purpose.setPurposeName(key);
        purpose.setAllowedFormats(allowedFormats);
        purpose.setEnabled(true);
        when(petSpaceModelPurposeMapper.selectOne(any())).thenReturn(purpose);
    }

    @Test
    @DisplayName("新增模型默认停用，需运营手动启用")
    void create_defaultsToDisabled() {
        stubPurpose("SCENE", "GLB,GLTF");
        PetSpaceModelEntity entity = buildValidEntity(null, "scene", null);

        service.create(entity);

        assertEquals("SCENE", entity.getModelKey());
        assertEquals("DISABLED", entity.getStatus());
        verify(petSpaceModelMapper).insert(entity);
    }

    @Test
    @DisplayName("新增模型用途不存在时拒绝")
    void create_invalidModelKey_rejected() {
        PetSpaceModelEntity entity = buildValidEntity(null, "UNKNOWN", null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.create(entity));
        assertTrue(error.getMessage().contains("用途"));
    }

    @Test
    @DisplayName("模型地址必须是站内上传路径或 http(s) 绝对地址")
    void create_invalidFileUrl_rejected() {
        stubPurpose("SCENE", "GLB,GLTF");
        PetSpaceModelEntity entity = buildValidEntity(null, "SCENE", null);
        entity.setFileUrl("javascript:alert(1)");

        assertThrows(IllegalArgumentException.class, () -> service.create(entity));
    }

    @Test
    @DisplayName("模型格式必须在该用途允许的格式范围内")
    void create_formatMismatch_rejected() {
        stubPurpose("SCENE", "GLB,GLTF");
        PetSpaceModelEntity sceneWithFbx = buildValidEntity(null, "SCENE", null);
        sceneWithFbx.setFileFormat("FBX");
        assertThrows(IllegalArgumentException.class, () -> service.create(sceneWithFbx));
    }

    @Test
    @DisplayName("启用模型时同用途其它版本自动停用")
    void enable_disablesOtherVersions() {
        stubPurpose("SCENE", "GLB,GLTF");
        PetSpaceModelEntity target = buildValidEntity(1L, "SCENE", "DISABLED");
        when(petSpaceModelMapper.selectById(1L)).thenReturn(target);

        service.enable(1L);

        verify(petSpaceModelMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(petSpaceModelMapper).updateById(target);
        assertEquals("ENABLED", target.getStatus());
    }

    @Test
    @DisplayName("启用中的模型不允许删除")
    void delete_enabledModel_rejected() {
        PetSpaceModelEntity target = buildValidEntity(1L, "SCENE", "ENABLED");
        when(petSpaceModelMapper.selectById(1L)).thenReturn(target);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.delete(1L));
        assertTrue(error.getMessage().contains("启用中"));
    }

    @Test
    @DisplayName("停用状态的模型可以删除")
    void delete_disabledModel_allowed() {
        PetSpaceModelEntity target = buildValidEntity(2L, "SCENE", "DISABLED");
        when(petSpaceModelMapper.selectById(2L)).thenReturn(target);

        service.delete(2L);

        verify(petSpaceModelMapper).deleteById(2L);
    }

    @Test
    @DisplayName("编辑时用途与状态保持原值，避免绕过启用流程")
    void update_keepsModelKeyAndStatus() {
        stubPurpose("SCENE", "GLB,GLTF");
        PetSpaceModelEntity existing = buildValidEntity(3L, "SCENE", "ENABLED");
        when(petSpaceModelMapper.selectById(3L)).thenReturn(existing);

        PetSpaceModelEntity update = buildValidEntity(3L, "AVATAR_WALK", "DISABLED");
        update.setDisplayName("新名称");
        service.update(update);

        assertEquals("SCENE", update.getModelKey());
        assertEquals("ENABLED", update.getStatus());
        verify(petSpaceModelMapper).updateById(update);
    }
}

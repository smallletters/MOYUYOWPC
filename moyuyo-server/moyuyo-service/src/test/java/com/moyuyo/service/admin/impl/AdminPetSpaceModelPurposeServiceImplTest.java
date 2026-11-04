package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminPetSpaceModelPurposeServiceImpl 单元测试
 * 覆盖：格式归一、用途标识校验、标识重复、内置用途/被引用用途禁止删除、编辑时标识不可变
 */
@ExtendWith(MockitoExtension.class)
class AdminPetSpaceModelPurposeServiceImplTest {

    @Mock
    private PetSpaceModelPurposeMapper purposeMapper;

    @Mock
    private PetSpaceModelMapper modelMapper;

    @InjectMocks
    private AdminPetSpaceModelPurposeServiceImpl service;

    @BeforeAll
    static void initMybatisTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetSpaceModelPurposeEntity.class);
        TableInfoHelper.initTableInfo(assistant, PetSpaceModelEntity.class);
    }

    private PetSpaceModelPurposeEntity buildPurpose(Long id, String key, String formats) {
        PetSpaceModelPurposeEntity entity = new PetSpaceModelPurposeEntity();
        entity.setId(id);
        entity.setPurposeKey(key);
        entity.setPurposeName("测试用途");
        entity.setAllowedFormats(formats);
        entity.setSortOrder(10);
        entity.setEnabled(true);
        return entity;
    }

    @Test
    @DisplayName("新增用途时格式归一为去重的大写列表")
    void create_normalizesFormats() {
        when(purposeMapper.selectCount(any())).thenReturn(0L);
        PetSpaceModelPurposeEntity entity = buildPurpose(null, "custom_room", "glb, GLTF, glb");

        service.create(entity);

        assertEquals("CUSTOM_ROOM", entity.getPurposeKey());
        assertEquals("GLB,GLTF", entity.getAllowedFormats());
        verify(purposeMapper).insert(entity);
    }

    @Test
    @DisplayName("用途标识格式不合法时拒绝")
    void create_invalidKey_rejected() {
        PetSpaceModelPurposeEntity entity = buildPurpose(null, "1ROOM", "GLB");

        assertThrows(IllegalArgumentException.class, () -> service.create(entity));
    }

    @Test
    @DisplayName("用途标识重复时拒绝")
    void create_duplicateKey_rejected() {
        PetSpaceModelPurposeEntity entity = buildPurpose(null, "ROOM", "GLB");
        when(purposeMapper.selectCount(any())).thenReturn(1L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.create(entity));
        assertTrue(error.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("内置用途禁止删除")
    void delete_builtInPurpose_rejected() {
        PetSpaceModelPurposeEntity entity = buildPurpose(1L, "SCENE", "GLB,GLTF");
        when(purposeMapper.selectById(1L)).thenReturn(entity);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.delete(1L));
        assertTrue(error.getMessage().contains("内置用途"));
    }

    @Test
    @DisplayName("用途下仍有模型时禁止删除")
    void delete_referencedPurpose_rejected() {
        PetSpaceModelPurposeEntity entity = buildPurpose(4L, "CUSTOM_ROOM", "GLB");
        when(purposeMapper.selectById(4L)).thenReturn(entity);
        when(modelMapper.selectCount(any())).thenReturn(1L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.delete(4L));
        assertTrue(error.getMessage().contains("已有模型"));
    }

    @Test
    @DisplayName("未被引用的自定义用途可以删除")
    void delete_unreferencedPurpose_allowed() {
        PetSpaceModelPurposeEntity entity = buildPurpose(5L, "CUSTOM_ITEM", "GLB");
        when(purposeMapper.selectById(5L)).thenReturn(entity);
        when(modelMapper.selectCount(any())).thenReturn(0L);

        service.delete(5L);

        verify(purposeMapper).deleteById(5L);
    }

    @Test
    @DisplayName("编辑用途时标识保持原值")
    void update_keepsPurposeKey() {
        PetSpaceModelPurposeEntity existing = buildPurpose(6L, "CUSTOM_ROOM", "GLB");
        when(purposeMapper.selectById(6L)).thenReturn(existing);

        PetSpaceModelPurposeEntity update = buildPurpose(6L, "NEW_KEY", "GLB,GLTF");
        service.update(update);

        assertEquals("CUSTOM_ROOM", update.getPurposeKey());
        verify(purposeMapper).updateById(update);
    }
}

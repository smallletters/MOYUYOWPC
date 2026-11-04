package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.PetSpaceModelEntity;

import java.util.List;

/**
 * 管理后台 APP 宠物 3D 空间模型服务
 */
public interface AdminPetSpaceModelService {

    /**
     * 模型列表（按用途 / 状态筛选）
     */
    List<PetSpaceModelEntity> list(String modelKey, String status);

    /**
     * 新增模型（默认停用，需手动启用）
     */
    void create(PetSpaceModelEntity entity);

    /**
     * 更新模型元信息（用途与状态不允许通过此接口修改）
     */
    void update(PetSpaceModelEntity entity);

    /**
     * 启用指定模型：同一用途下的其它版本自动停用
     */
    void enable(Long id);

    /**
     * 删除模型（启用中的模型不允许删除）
     */
    void delete(Long id);

    /**
     * APP 端使用：查询所有已启用模型
     */
    List<PetSpaceModelEntity> listEnabled();
}

package com.moyuyo.service.admin;

import com.moyuyo.dao.admin.entity.PetSpaceModelPurposeEntity;

import java.util.List;

/**
 * 管理后台 APP 宠物 3D 空间模型用途服务
 */
public interface AdminPetSpaceModelPurposeService {

    /**
     * 全部用途（含停用），按排序值升序
     */
    List<PetSpaceModelPurposeEntity> listAll();

    /**
     * 仅启用用途，供模型表单与 APP 配置筛选使用
     */
    List<PetSpaceModelPurposeEntity> listEnabled();

    /**
     * 新增用途
     */
    void create(PetSpaceModelPurposeEntity entity);

    /**
     * 更新用途（用途标识创建后不可修改）
     */
    void update(PetSpaceModelPurposeEntity entity);

    /**
     * 删除用途（内置用途或仍被模型引用的用途不允许删除）
     */
    void delete(Long id);
}

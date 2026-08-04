package com.moyuyo.service.admin;

import java.util.Set;

/**
 * 管理后台权限查询服务（接口级 RBAC）
 * 权限键格式：resource:action，如 products:view / orders:edit
 */
public interface AdminPermissionService {

    /**
     * 获取角色的权限键集合（带 Redis 缓存）。
     * 角色不存在或已禁用时返回空集合。
     */
    Set<String> getPermKeys(String roleCode);

    /**
     * 清除指定角色的权限缓存（角色权限/状态变更后调用）
     */
    void evict(String roleCode);
}

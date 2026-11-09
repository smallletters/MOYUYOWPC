package com.moyuyo.service.admin;

/**
 * 管理端账号状态服务：按管理员 ID 查询当前有效的角色。
 * <p>
 * 用于修复“禁用/改角色后旧 token 仍可用”的问题：JWT 中的角色是签发时的快照，
 * 管理端每个请求都需要对比数据库中的最新状态。为避免每请求查库，使用 60 秒 Redis 缓存；
 * 管理端修改账号状态/角色时主动 evict，保证立即生效。
 */
public interface AdminAccountStateService {

    /**
     * 返回管理员当前有效角色；账号不存在、非 ACTIVE 或读取失败时返回 null。
     */
    String getActiveRole(Long adminId);

    /** 清除指定管理员的账号状态缓存（禁用/删除/改角色后调用） */
    void evict(Long adminId);
}

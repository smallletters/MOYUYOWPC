package com.moyuyo.common.security;

import com.moyuyo.common.exception.BusinessException;

public class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 获取当前登录用户 ID（必须登录语义），未登录直接抛 401。
     * <p>
     * 与 {@link #getUserId()} 的区别：后者返回 null，只适用于"可选身份"场景
     * （如公开浏览接口按登录用户标记 liked / collected 状态）。凡是必须登录才能调用的接口
     * 都应使用本方法，避免依赖"userId 为 null 时恰好查不到数据"的隐含假设。
     */
    public static Long requireUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    public static void setToken(String token) {
        TOKEN_HOLDER.set(token);
    }

    public static String getToken() {
        return TOKEN_HOLDER.get();
    }

    /** 设置当前用户角色（仅管理端 token 携带） */
    public static void setRole(String role) {
        ROLE_HOLDER.set(role);
    }

    /** 获取当前用户角色，无角色时返回 null */
    public static String getRole() {
        return ROLE_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        TOKEN_HOLDER.remove();
        ROLE_HOLDER.remove();
    }

  /**
   * 获取当前操作者标识（优先用户名，回退到用户ID）
   */
  public static String getOperator() {
    Long userId = USER_ID_HOLDER.get();
    return userId != null ? String.valueOf(userId) : "系统";
  }
}

package com.moyuyo.service;

import com.moyuyo.common.dto.auth.*;
import com.moyuyo.dao.entity.UserEntity;

public interface AuthService {

    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refreshToken(RefreshTokenRequest request);

    void logout(Long userId, String accessToken);

    void sendEmailVerification(EmailVerifyRequest request);

    void confirmEmailVerification(EmailVerifyConfirmRequest request);

    void sendPasswordReset(EmailVerifyRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    UserEntity getCurrentUser(Long userId);

    UserEntity updateCurrentUser(Long userId, ProfileUpdateRequest update);

    void sendMagicLink(EmailVerifyRequest request);

    TokenResponse verifyMagicLink(String token);

    void sendTwoFactorCode(Long userId);

    void verifyTwoFactorCode(Long userId, String code);

    /**
     * 设置两步验证开关。
     * 关闭时同时清掉 {@code auth:2fa-verified:userId} 缓存,
     * 确保下一次敏感操作(登录 / 大额支付)重新走二次校验。
     */
    UserEntity setTwoFactorEnabled(Long userId, boolean enabled);

    /**
     * 发送手机验证码。
     * @param phone 手机号（含国家区号）
     * @param purpose LOGIN / REGISTER / RESET_PASSWORD
     */
    void sendPhoneCode(String phone, String purpose);

    /**
     * 手机号 + 验证码登录。
     * 若手机号未注册则自动创建账号（生成随机密码、默认昵称），已注册则返回 JWT。
     */
    TokenResponse loginByPhone(String phone, String code);

    /**
     * 申请注销账户。
     * <p>
     * 流程（参考微信 15 天/京东 30 天冻结期模式）：
     * <ol>
     *   <li>校验账户无未完成订单（避免死单）</li>
     *   <li>写入 {@code delete_scheduled_at = now + DELETION_GRACE_DAYS},
     *       进入 {@code PENDING_DELETE} 状态</li>
     *   <li>吊销该用户全部 access/refresh token（即时冻结）</li>
     *   <li>冻结期内用户主动撤销（{@link #cancelAccountDeletion}）或重新登录
     *       都会清空该字段恢复 {@code ACTIVE}</li>
     *   <li>定时任务到期后将用户 PII 匿名化、软删关联数据</li>
     * </ol>
     *
     * @return 写入后的 {@code deleteScheduledAt}（前端展示倒计时）
     */
    java.time.LocalDateTime requestAccountDeletion(Long userId);

    /**
     * 撤销注销申请。
     * <p>
     * 仅当 {@code delete_scheduled_at} 未到期时允许撤销；已到期由定时任务
     * 清理后此接口直接返回当前状态即可（前端应引导用户重新注册）。
     */
    void cancelAccountDeletion(Long userId);

    /**
     * 查询注销状态。
     *
     * @return 注销信息 VO；用户不存在时返回 {@code null}
     */
    DeletionStatus getDeletionStatus(Long userId);

    /** 注销状态查询 VO */
    @lombok.Data
    @lombok.AllArgsConstructor
    class DeletionStatus {
        /** 是否已提交注销申请 */
        private boolean pending;
        /** 注销计划执行时间（epoch 毫秒）；未提交时为 null */
        private Long scheduledAtMillis;
        /** 距到期剩余秒数；未提交或已到期时为 0 */
        private long remainingSeconds;
        /** 当前状态：ACTIVE / PENDING_DELETE */
        private String status;
    }

    /**
     * 请求数据导出：1 天内同一账号最多 1 次，超出抛 429。
     * <p>
     * 返回导出请求 ID 与下次可发起时间（epoch 毫秒）。
     * 实际数据生成由定时任务异步处理，文件生成后通过邮件发送至注册邮箱。
     *
     * @throws com.moyuyo.common.exception.BusinessException 429 频次超限 / 404 用户不存在
     */
    DataExportAck requestDataExport(Long userId);

    /** 数据导出请求响应 VO */
    @lombok.Data
    @lombok.AllArgsConstructor
    class DataExportAck {
        /** 导出任务 ID */
        private Long requestId;
        /** 状态：PENDING / PROCESSING */
        private String status;
        /** 当前时间（epoch 毫秒） */
        private long nowMillis;
        /** 下次可发起时间（epoch 毫秒），1 天内不可重复 */
        private Long nextAllowedAtMillis;
    }
}

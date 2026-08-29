package com.moyuyo.service;

/**
 * 邮件发送服务：密码重置、邮箱验证、Magic Link 等业务邮件统一入口。
 * <p>
 * dev 未配置 SMTP 时 {@link com.moyuyo.service.impl.EmailServiceImpl} 内部为
 * "fail-open + 日志告警"模式，不会阻塞业务接口返回；生产需强制通过
 * SPRING_MAIL_HOST / SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD 注入真实 SMTP 配置。
 */
public interface EmailService {

    /**
     * 发送"密码重置验证码"邮件。
     * @param to 收件人邮箱（已规范化）
     * @param code 6 位数字验证码
     * @param ttlMinutes 验证码有效分钟数（仅用于正文展示）
     */
    void sendPasswordResetCode(String to, String code, int ttlMinutes);

    /**
     * 发送"邮箱注册验证"邮件。
     * @param to 收件人邮箱（已规范化）
     * @param code 6 位数字验证码
     * @param ttlMinutes 验证码有效分钟数（仅用于正文展示）
     */
    void sendEmailVerificationCode(String to, String code, int ttlMinutes);

    /**
     * 发送"Magic Link 一键登录"邮件。
     * @param to 收件人邮箱（已规范化）
     * @param magicLink 完整可点击登录链接（前端基础地址 + token）
     * @param ttlMinutes 链接有效分钟数（仅用于正文展示）
     */
    void sendMagicLink(String to, String magicLink, int ttlMinutes);
}

package com.moyuyo.service;

/**
 * 邮件发送服务：密码重置、邮箱验证、Magic Link 等业务邮件统一入口。
 * <p>
 * {@link com.moyuyo.service.impl.EmailServiceImpl} 的失败策略：
 * dev profile 未配置 SMTP 时走 log-only（不输出验证码，不阻断本地联调）；
 * 非 dev 环境未配置 SMTP 时抛 503，发送失败抛 502，避免"接口成功但邮件未发"。
 * 生产必须通过 SPRING_MAIL_HOST / SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD 注入真实 SMTP。
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
     * 发送"两步验证"邮件。
     * <p>
     * 与 {@link #sendEmailVerificationCode} 的区别:此邮件用于"开启 2FA 前的二次身份校验",
     * 正文中需明确告知"你正在修改账号安全设置",降低被钓鱼利用的风险。
     * @param to 收件人邮箱（已规范化）
     * @param code 6 位数字验证码
     * @param ttlMinutes 验证码有效分钟数（仅用于正文展示）
     */
    void sendTwoFactorCode(String to, String code, int ttlMinutes);

    /**
     * 发送"Magic Link 一键登录"邮件。
     * @param to 收件人邮箱（已规范化）
     * @param magicLink 完整可点击登录链接（前端基础地址 + token）
     * @param ttlMinutes 链接有效分钟数（仅用于正文展示）
     */
    void sendMagicLink(String to, String magicLink, int ttlMinutes);

    /**
     * 发送运营通知邮件（推送管理群发场景逐用户调用）。
     * @param to 收件人邮箱
     * @param subject 邮件主题（推送标题）
     * @param content 邮件正文（纯文本，内部转义后以 HTML 展示）
     */
    void sendNotification(String to, String subject, String content);
}

package com.moyuyo.service.impl;

import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * JavaMailSender 实现：通过 spring-boot-starter-mail 自动装配的 JavaMailSender
 * 发送 HTML 格式业务邮件。
 * <p>
 * 可靠性策略（与 SmsService 保持一致）：
 * 1) 使用 {@link org.springframework.mail.javamail.JavaMailSender} 真实 SMTP 发送；
 * 2) 若 SMTP 未配置（host/username/password 全空，本地开发常见场景）降级为
 *    "log-only 模式"——不抛异常，避免阻断本地联调，邮件正文打 WARN 日志，
 *    配合 MailHog（SMTP 默认即 127.0.0.1:1025）可真实接收；
 * 3) 若部分配置存在但发送失败，抛出 502 BusinessException，前端提示"邮件发送失败"
 *    避免用户误以为成功，导致忘记密码流程无法继续。
 * <p>
 * 注：使用 ObjectProvider&lt;JavaMailSender&gt; 可选注入，避免生产 SMTP 临时撤下或
 * 本地忘记启动 MailHog 时因 MailSenderAutoConfiguration 条件触发差异导致启动阻塞。
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    /** JavaMailSender 可选注入：未配置真实 SMTP 时为 null，走 log-only 降级 */
    private final JavaMailSender javaMailSender;

    /** 发件邮箱（生产必填） */
    @Value("${moyuyo.mail.from:noreply@moyuyo.local}")
    private String fromAddress;

    /** 发件人显示名称 */
    @Value("${moyuyo.mail.from-name:MOYUYO Support}")
    private String fromPersonal;

    /**
     * H5 端基础地址，用于 Magic Link 拼接。
     * dev 默认 http://localhost:5174（与 uni-app 端口一致），
     * 生产通过 MOYUYO_H5_BASE 覆盖。
     */
    @Value("${moyuyo.h5-base:http://localhost:5174}")
    private String h5Base;

    /** 是否真实走 SMTP（dev 未配置时自动切换为 log-only） */
    @Value("${moyuyo.mail.enabled:true}")
    private boolean mailEnabled;

    /**
     * 使用 ObjectProvider 可选注入 JavaMailSender：
     * - 配置了 spring.mail.host 时 MailSenderAutoConfiguration 会注册 Bean，此处正常拿到实例；
     * - 本地未起 MailHog 且未填 env 时 Bean 可能不存在 → javaMailSender 为 null → send() 走 log-only。
     */
    public EmailServiceImpl(ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        this.javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (this.javaMailSender == null) {
            log.warn("[mail] JavaMailSender Bean 未注册（spring.mail.* 未配置），" +
                    "所有业务邮件将进入 log-only 降级模式；请配置 MailHog（127.0.0.1:1025）或真实 SMTP。");
        }
    }

    @Override
    public void sendPasswordResetCode(String to, String code, int ttlMinutes) {
        String subject = "Reset your MOYUYO password";
        // 关键信息：code 只在构建邮件正文时出现，不落日志
        String html = buildHtml(
                "Reset your password",
                "Your password reset code is:",
                // 6 位数字用大号字体展示，便于移动端直接抄录
                "<div style=\"font-size:36px;font-weight:700;letter-spacing:8px;color:#111;margin:16px 0;\">" + code + "</div>",
                "This code expires in " + ttlMinutes + " minutes. If you didn't request a password reset, you can safely ignore this email.",
                null
        );
        send(to, subject, html, "sendPasswordResetCode", code);
    }

    @Override
    public void sendEmailVerificationCode(String to, String code, int ttlMinutes) {
        String subject = "Verify your MOYUYO email";
        String html = buildHtml(
                "Verify your email",
                "Your verification code is:",
                "<div style=\"font-size:36px;font-weight:700;letter-spacing:8px;color:#111;margin:16px 0;\">" + code + "</div>",
                "This code expires in " + ttlMinutes + " minutes. Please enter it in the app to complete email verification.",
                null
        );
        send(to, subject, html, "sendEmailVerificationCode", code);
    }

    @Override
    public void sendTwoFactorCode(String to, String code, int ttlMinutes) {
        // 单独 subject,便于用户在邮箱列表中快速识别"安全相关"邮件,
        // 同时与普通"注册验证"邮件区分,降低误读风险
        String subject = "Your MOYUYO two-factor authentication code";
        String html = buildHtml(
                "Two-factor authentication",
                "Use this code to confirm enabling two-factor authentication on your MOYUYO account:",
                "<div style=\"font-size:36px;font-weight:700;letter-spacing:8px;color:#111;margin:16px 0;\">" + code + "</div>",
                "This code expires in " + ttlMinutes + " minutes. " +
                "If you didn't request to enable two-factor authentication, please change your account password immediately.",
                // 额外说明:避免用户对此类邮件产生误判(被钓鱼利用)
                "Why am I getting this? You (or someone with access to your account) " +
                "are about to turn on two-factor authentication. We never ask for it by phone."
        );
        send(to, subject, html, "sendTwoFactorCode", code);
    }

    @Override
    public void sendMagicLink(String to, String magicLink, int ttlMinutes) {
        String subject = "Sign in to MOYUYO with one click";
        String html = buildHtml(
                "Your sign-in link",
                "Click the button below to sign in directly (no password required):",
                // 按钮样式：US 购物 App 惯用蓝色 CTA
                "<a href=\"" + escapeAttr(magicLink) + "\" " +
                        "style=\"display:inline-block;padding:14px 28px;background:#111;color:#fff;" +
                        "text-decoration:none;border-radius:12px;font-weight:600;margin:16px 0;\">" +
                        "Sign in to MOYUYO" +
                        "</a>",
                "This link expires in " + ttlMinutes + " minutes. If you didn't request it, ignore this email.",
                "Or copy and paste this URL into your browser:<br/>" +
                        "<span style=\"word-break:break-all;color:#444;\">" + escapeText(magicLink) + "</span>"
        );
        // magicLink 包含 token，不落日志
        send(to, subject, html, "sendMagicLink", null);
    }

    // ======================= 内部辅助 =======================

    /**
     * 统一发送入口：SMTP 发送 + 异常兜底 + dev 降级告警。
     * @param to 收件人
     * @param subject 主题
     * @param html 正文 HTML
     * @param actionName 业务动作名（仅日志/异常语义）
     * @param sensitiveValue 敏感值（纯 dev log-only 模式下，才会在日志里追加前缀「dev-only:」输出，
     *                       便于 MailHog 没起时也能复制验证码继续联调；生产不输出）。
     */
    private void send(String to, String subject, String html, String actionName, String sensitiveValue) {
        // 1) 未启用邮件时：直接 log-only 降级，不抛异常
        if (!mailEnabled) {
            log.warn("[mail][dev-only] {} 邮件跳过真实发送（moyuyo.mail.enabled=false），" +
                            "to={}, sensitive={}",
                    actionName, mask(to), sensitiveValue == null ? "n/a" : "dev-only:" + sensitiveValue);
            return;
        }

        // 2) JavaMailSender Bean 未注册（未填 spring.mail.* / 环境未装配）→ log-only 降级，
        //    不抛异常；但在 dev-only 模式下把敏感值输出，便于不启 MailHog 时也能复制验证码继续联调
        if (javaMailSender == null) {
            log.warn("[mail][dev-only] {} 邮件因无 JavaMailSender Bean 进入降级发送，" +
                            "to={}, sensitive={}",
                    actionName, mask(to), sensitiveValue == null ? "n/a" : "dev-only:" + sensitiveValue);
            return;
        }

        // 3) 真实发送：MimeMessage 组装 + JavaMailSender.send()
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime,
                    MimeMessageHelper.MULTIPART_MODE_NO,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(buildFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            javaMailSender.send(mime);
            log.info("[mail] {} 发送成功, to={}", actionName, mask(to));
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            // 真实配置存在但发送失败：抛 502，避免用户误以为"已发"
            log.error("[mail] {} 发送失败, to={}", actionName, mask(to), e);
            throw new BusinessException(502, "邮件发送失败，请稍后再试");
        }
    }

    /**
     * 组装 From 地址：支持 "显示名称 <邮箱>" 格式，且显示名称做 UTF-8 编码，
     * 避免中文在部分 SMTP 服务商乱码。
     * <p>
     * 抛 AddressException 属于 MessagingException 子类，已在 send() 的 catch 中统一兜底；
     * 抛 UnsupportedEncodingException 同理（不会真的发生，UTF-8 永远被 JVM 支持）。
     */
    private InternetAddress buildFrom() throws MessagingException, UnsupportedEncodingException {
        if (!StringUtils.hasText(fromPersonal)) {
            return new InternetAddress(fromAddress);
        }
        return new InternetAddress(fromAddress, fromPersonal, StandardCharsets.UTF_8.name());
    }

    /**
     * 构建业务通用 HTML 模板：美式购物 App 风格（顶部品牌、居中 CTA、页脚链接），
     * 纯字符串拼接避免引入模板引擎依赖，样式内联保证主流邮件客户端兼容。
     */
    private String buildHtml(String title, String lead, String heroBlock, String note, String extra) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset=\"utf-8\"/></head>" +
                "<body style=\"margin:0;padding:0;background:#f6f6f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;color:#111;\">" +
                "<div style=\"max-width:560px;margin:24px auto;background:#fff;border-radius:16px;padding:32px 28px;box-shadow:0 1px 2px rgba(0,0,0,0.04);\">" +
                "  <div style=\"font-size:22px;font-weight:700;letter-spacing:1px;\">MOYUYO</div>" +
                "  <h1 style=\"font-size:24px;margin:16px 0 8px;\">" + escapeText(title) + "</h1>" +
                "  <p style=\"font-size:15px;line-height:1.6;margin:0 0 8px;color:#333;\">" + escapeText(lead) + "</p>" +
                "  <div>" + heroBlock + "</div>" +
                "  <p style=\"font-size:14px;line-height:1.6;color:#555;margin:8px 0 0;\">" + escapeText(note) + "</p>" +
                (extra == null ? "" : "<div style=\"margin:16px 0 0;font-size:13px;line-height:1.6;color:#555;\">" + extra + "</div>") +
                "  <div style=\"margin-top:24px;padding-top:16px;border-top:1px solid #eee;font-size:12px;color:#999;line-height:1.6;\">" +
                "    &copy; " + java.time.Year.now().getValue() + " MOYUYO. All rights reserved." +
                "  </div>" +
                "</div></body></html>";
    }

    /** 简单 HTML 正文转义，避免注入破坏结构（业务内容都受控，轻量处理即可） */
    private String escapeText(String src) {
        if (src == null) return "";
        return src.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** HTML 属性值转义（主要用于 a 标签 href） */
    private String escapeAttr(String src) {
        return escapeText(src);
    }

    /** 日志邮箱脱敏：前 3 位 + *** + @ + 后 1 位域名，避免全量泄露 */
    private String mask(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String prefix = local.length() <= 3 ? local : local.substring(0, 3);
        return prefix + "***" + domain;
    }
}

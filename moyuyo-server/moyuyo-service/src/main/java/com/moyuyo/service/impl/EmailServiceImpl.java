package com.moyuyo.service.impl;

import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JavaMailSender 实现：通过 spring-boot-starter-mail 自动装配的 JavaMailSender
 * 发送 HTML 格式业务邮件。
 * <p>
 * 可靠性策略（与 SmsService 保持一致）：
 * 1) 使用 {@link org.springframework.mail.javamail.JavaMailSender} 真实 SMTP 发送；
 * 2) dev profile 未配置 SMTP（host/username/password 为空或 Bean 缺失）时，
 *    降级为 log-only：只记录动作名与脱敏收件人，不输出验证码，不阻断本地联调；
 * 3) 非 dev 环境（prod / test / 预发）未配置 SMTP 时 fail-closed，统一抛 503，
 *    避免出现"接口返回成功但用户收不到验证码/重置邮件"；
 * 4) 配置存在但发送失败时抛出 502 BusinessException，前端提示"邮件发送失败"。
 * <p>
 * 注：使用 ObjectProvider&lt;JavaMailSender&gt; 可选注入，避免生产 SMTP 临时撤下或
 * 本地忘记启动 MailHog 时因 MailSenderAutoConfiguration 条件触发差异导致启动阻塞。
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    /** JavaMailSender 可选注入：未配置真实 SMTP 时为 null，走 dev log-only / 非 dev fail-closed */
    private final JavaMailSender javaMailSender;

    /** 运行环境：仅 dev profile 允许邮件 log-only 降级，其余环境一律失败 */
    private final Environment environment;

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
    public EmailServiceImpl(ObjectProvider<JavaMailSender> javaMailSenderProvider, Environment environment) {
        this.javaMailSender = javaMailSenderProvider.getIfAvailable();
        this.environment = environment;
        if (this.javaMailSender == null) {
            log.warn("[mail] JavaMailSender Bean 未注册（spring.mail.* 未配置）；"
                    + "dev 将走 log-only，非 dev 环境发送邮件时直接失败");
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
        send(to, subject, html, "sendPasswordResetCode");
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
        send(to, subject, html, "sendEmailVerificationCode");
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
        send(to, subject, html, "sendTwoFactorCode");
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
        send(to, subject, html, "sendMagicLink");
    }

    @Override
    public void sendNotification(String to, String subject, String content) {
        // 正文使用 pre-wrap 保留用户输入的换行；escapeText 防止 HTML 注入
        String html = buildHtml(
                subject,
                "You have a new message from MOYUYO:",
                "<div style=\"font-size:15px;line-height:1.6;color:#333;white-space:pre-wrap;margin:16px 0;\">" +
                        escapeText(content) + "</div>",
                "This message was sent by MOYUYO. You can manage your notification preferences in the app.",
                null
        );
        send(to, subject, html, "sendNotification");
    }

    // ======================= 内部辅助 =======================

    /**
     * 统一发送入口：SMTP 发送 + 异常兜底 + dev 降级告警。
     * <p>
     * 安全约定：日志只记录动作名与脱敏收件人，绝不输出验证码/重置链接等敏感值，
     * 避免生产环境漏配 SMTP 时验证码随日志外泄。
     */
    private void send(String to, String subject, String html, String actionName) {
        // 1) 邮件服务不可用：统一失败策略
        //    - dev：仅告警并按 log-only 降级，便于本地无 MailHog 联调
        //    - 非 dev（生产/测试/预发等）：明确抛 503，避免"接口成功但邮件未发"
        if (!mailEnabled || javaMailSender == null) {
            if (!isDevProfile()) {
                log.error("[mail] 邮件服务未配置，拒绝发送: action={}, to={}", actionName, mask(to));
                throw new BusinessException(503, "邮件服务未配置，请联系管理员");
            }
            log.warn("[mail][dev-only] {} 邮件未真实发送（未配置 SMTP 或已禁用），to={}",
                    actionName, mask(to));
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
     * 是否 dev profile。
     * <p>
     * 仅 dev 允许邮件 log-only 降级；其余环境（含 test/prod/预发）在邮件未配置时
     * 一律 fail-closed，避免出现"接口返回成功但用户收不到验证码/重置邮件"。
     */
    private boolean isDevProfile() {
        if (environment == null) {
            return false;
        }
        String[] active = environment.getActiveProfiles();
        return active != null && Arrays.asList(active).contains("dev");
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

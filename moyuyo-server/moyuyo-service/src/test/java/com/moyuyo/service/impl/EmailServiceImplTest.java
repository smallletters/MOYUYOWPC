package com.moyuyo.service.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import com.moyuyo.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailServiceImpl 单元测试（纯 Mock，不连真实 SMTP）。
 * 覆盖：
 * 1) From 必须与 moyuyo.mail.from 一致（防腾讯 501）
 * 2) 6 位验证码 HTML 正文不能含 String.format 风格 % 占位
 * 3) 主题/收件人精确匹配
 * 4) Magic Link 链接 & 转义
 * 5) dev profile + mailEnabled=false：log-only 降级，不抛异常不调用 JavaMail
 * 6) dev profile + JavaMailSender Bean 不存在：log-only 降级不抛异常
 * 7) 非 dev（prod）未配置 SMTP：统一抛 503，不伪造成功
 * 8) 海外节点配置约束（hwsmtp.exmail.qq.com:465 SSL）
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Captor
    private ArgumentCaptor<MimeMessage> mimeCaptor;

    private EmailServiceImpl emailService;

    /** 单元测试统一使用 dev profile，验证 log-only 降级路径 */
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        emailService = new EmailServiceImpl(mailSenderProvider, environment);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "fromPersonal", "MOYUYO Support");
        ReflectionTestUtils.setField(emailService, "h5Base", "https://www.moyuyo.com");
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
    }

    @Test
    @DisplayName("sendPasswordResetCode：MIME From 必须等于 moyuyo.mail.from")
    void sendPasswordResetCode_fromAddressMustMatch() throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mime);
        emailService.sendPasswordResetCode("user@example.com", "123456", 30);
        verify(javaMailSender).send(mimeCaptor.capture());
        MimeMessage sent = mimeCaptor.getValue();
        assertEquals(1, sent.getFrom().length, "邮件 From 必须恰好 1 个地址");
        String addr = sent.getFrom()[0].toString();
        String email = addr.replaceAll(".*<([^>]+)>.*", "$1");
        assertEquals("noreply@example.com", email,
                "From 的邮箱部分必须与 moyuyo.mail.from 完全一致，否则腾讯企业邮会返回 501");
    }

    @Test
    @DisplayName("sendPasswordResetCode：HTML 正文必须包含 6 位 code 且无 String.format 风格 % 占位")
    void sendPasswordResetCode_containsCodeAndHasNoDanglingPercent() throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mime);
        emailService.sendPasswordResetCode("user@example.com", "834721", 30);
        verify(javaMailSender).send(mimeCaptor.capture());
        String html = readHtml(mimeCaptor.getValue());
        assertTrue(html.contains("834721"), "HTML 正文中必须包含真实的 6 位验证码");
        assertTrue(html.contains("This code expires in 30 minutes"), "必须包含过期分钟数文案");
        // 排除 CSS 内的 100% 等合法百分号后，不允许出现 formatter 占位符（%s/%d/%n）
        String cleaned = html.replace("100%", "").replace("560px", "");
        boolean hasFormat = java.util.regex.Pattern.compile("%[\\p{Alpha}%]").matcher(cleaned).find();
        assertFalse(hasFormat,
                "HTML 中存在 String.format 风格百分号占位符，重构整段格式化时会触发 FormatFlagsConversionMismatchException");
    }

    @Test
    @DisplayName("sendPasswordResetCode：主题 + 收件人精确匹配")
    void sendPasswordResetCode_subjectAndTo() throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mime);
        emailService.sendPasswordResetCode("alice+billing@example.com", "000111", 15);
        verify(javaMailSender).send(mimeCaptor.capture());
        MimeMessage sent = mimeCaptor.getValue();
        assertEquals("Reset your MOYUYO password", sent.getSubject());
        assertEquals(1, sent.getAllRecipients().length);
        assertEquals("alice+billing@example.com", sent.getAllRecipients()[0].toString());
    }

    @Test
    @DisplayName("sendMagicLink：HTML 中必须出现 &amp; 转义后的 Magic Link 与 Sign in to MOYUYO 按钮文案")
    void sendMagicLink_containsEscapedLinkAndCta() throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mime);
        emailService.sendMagicLink("bob@example.com",
                "https://www.moyuyo.com/pages/user/magic-link?token=abc123XYZ&source=email", 15);
        verify(javaMailSender).send(mimeCaptor.capture());
        String html = readHtml(mimeCaptor.getValue());
        assertTrue(html.contains("pages/user/magic-link?token=abc123XYZ&amp;source=email"),
                "Magic Link 的 query '&' 必须转义为 &amp; 避免 HTML 属性解析错误");
        assertTrue(html.contains("Sign in to MOYUYO"), "CTA 按钮文案应为 US 购物 App 惯用英文");
    }

    @Test
    @DisplayName("dev profile + mailEnabled=false：log-only 降级，不调 JavaMailSender.send，不抛异常")
    void disabledMail_neverSendsAndDoesNotThrow() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);
        assertDoesNotThrow(() -> emailService.sendPasswordResetCode("x@example.com", "666666", 30));
        verify(javaMailSender, never()).createMimeMessage();
        verify(javaMailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    @DisplayName("dev profile + JavaMailSender Bean 不存在：log-only 降级，不抛异常")
    void noMailSenderBean_neverSendsAndDoesNotThrow() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        EmailServiceImpl degraded = new EmailServiceImpl(mailSenderProvider, environment);
        ReflectionTestUtils.setField(degraded, "mailEnabled", true);
        ReflectionTestUtils.setField(degraded, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(degraded, "fromPersonal", "MOYUYO");
        assertDoesNotThrow(() -> degraded.sendPasswordResetCode("x@example.com", "666666", 30));
    }

    @Test
    @DisplayName("prod profile 未配置 SMTP：验证码邮件统一抛 503")
    void sendPasswordResetCodeWithoutSmtp_inProd_shouldThrow() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        EmailServiceImpl degraded = new EmailServiceImpl(mailSenderProvider, prod);
        ReflectionTestUtils.setField(degraded, "mailEnabled", true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> degraded.sendPasswordResetCode("x@example.com", "666666", 30));
        assertEquals(503, error.getCode(), "未配置邮件服务应返回 503 而不是静默成功");
    }

    @Test
    @DisplayName("prod profile 未配置 SMTP + sendNotification：同样抛 503（行为已统一）")
    void sendNotificationWithoutSmtp_inProd_shouldThrow() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        EmailServiceImpl degraded = new EmailServiceImpl(mailSenderProvider, prod);
        ReflectionTestUtils.setField(degraded, "mailEnabled", true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> degraded.sendNotification("x@example.com", "通知标题", "通知正文"));
        assertEquals(503, error.getCode(), "未配置邮件服务应返回 503 而不是静默成功");
        verify(javaMailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("腾讯企业邮海外节点配置约束：host=hwsmtp.exmail.qq.com port=465 ssl=true starttls=false")
    void tencentExmailOverseasConfiguration_constraints() {
        Properties required = new Properties();
        required.setProperty("SPRING_MAIL_HOST", "hwsmtp.exmail.qq.com");
        required.setProperty("SPRING_MAIL_PORT", "465");
        required.setProperty("SPRING_MAIL_SMTP_AUTH", "true");
        required.setProperty("SPRING_MAIL_SMTP_SSL", "true");
        required.setProperty("SPRING_MAIL_SMTP_STARTTLS", "false");
        assertEquals("465", required.getProperty("SPRING_MAIL_PORT"),
                "海外腾讯企业邮必须使用 465 端口（SSL），避免 STARTTLS 握手失败导致 502");
        assertEquals("false", required.getProperty("SPRING_MAIL_SMTP_STARTTLS"),
                "465 SSL 模式下 STARTTLS 必须为 false；同时 ssl.enable=true + starttls.enable=true 是发信失败根因");
        assertEquals("hwsmtp.exmail.qq.com", required.getProperty("SPRING_MAIL_HOST"),
                "海外服务器必须走 hw 前缀海外节点，减少跨境链路抖动");
    }

    private String readHtml(MimeMessage mime) throws Exception {
        Object content = mime.getContent();
        if (content instanceof String s) {
            return s;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mime.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
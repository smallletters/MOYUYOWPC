package com.moyuyo.service.impl;

import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 短信服务兜底实现：dev profile 仅打日志不实际发送；非 dev profile 直接 fail-closed。
 *
 * 启用条件：moyuyo.sms.provider 不是 aliyun（包括为空/未配置）。
 * 与 {@link AliyunSmsServiceImpl} 的 @ConditionalOnProperty(provider=aliyun) 严格互斥。
 *
 * 注意：原实现使用 @ConditionalOnMissingBean，但该注解只在自动配置类中可靠生效；
 * 在普通 @Service 组件扫描下条件评估时机不可控，会导致 provider 为空时兜底 Bean
 * 未被注册，进而使依赖 SmsService 的 PushManageServiceImpl 启动失败。
 *
 * 生产环境必须配置 moyuyo.sms.provider=aliyun。非 dev 环境未配置时，
 * sendCode 会抛 503 并拒绝发送，避免验证码写入日志或“假发送成功”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${moyuyo.sms.provider:}' != 'aliyun'")
public class NoopSmsServiceImpl implements SmsService {

    private final Environment environment;

    @Override
    public void sendCode(String phone, String code, String purpose) {
        // 安全修复：非 dev 环境如果误用 Noop（未配置 provider=aliyun），
        // 不能把验证码写进日志，也不能假装发送成功，必须 fail-closed。
        if (!isDevProfile()) {
            log.error("[sms] NoopSmsService active in non-dev profile，验证码未真实发送，purpose={}；"
                    + "生产环境必须配置 moyuyo.sms.provider=aliyun", purpose);
            throw new BusinessException(503, "短信服务未配置，请联系管理员");
        }
        // 仅 dev 输出验证码，便于本地联调；生产环境不会走到这里
        log.warn("[sms][dev-only] NoopSmsService active. code={}, phone={}, purpose={}",
                code, phone, purpose);
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    @Override
    public void sendNotice(String phone, String title, String content) {
        // 群发场景必须明确失败：不能让管理员误以为短信已真实发出
        log.warn("[sms] NoopSmsService active, notice not sent. phone={}, title={}", phone, title);
        throw new BusinessException(503, "短信服务未配置，通知未发送");
    }
}
package com.moyuyo.service.impl;

import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * 短信服务兜底实现：仅打日志，不实际发送。
 *
 * 启用条件：moyuyo.sms.provider 不是 aliyun（包括为空/未配置）。
 * 与 {@link AliyunSmsServiceImpl} 的 @ConditionalOnProperty(provider=aliyun) 严格互斥。
 *
 * 注意：原实现使用 @ConditionalOnMissingBean，但该注解只在自动配置类中可靠生效；
 * 在普通 @Service 组件扫描下条件评估时机不可控，会导致 provider 为空时兜底 Bean
 * 未被注册，进而使依赖 SmsService 的 PushManageServiceImpl 启动失败。
 *
 * 生产环境必须配置 moyuyo.sms.provider=aliyun，否则手机号登录不可用。
 * 此 Bean 让应用在 dev 启动不报错，但日志会发出 WARN 提示。
 */
@Slf4j
@Service
@ConditionalOnExpression("'${moyuyo.sms.provider:}' != 'aliyun'")
public class NoopSmsServiceImpl implements SmsService {

    @Override
    public void sendCode(String phone, String code, String purpose) {
        log.warn("[sms] NoopSmsService active. code={}, phone={}, purpose={}. "
                        + "生产环境必须配置 moyuyo.sms.provider=aliyun 启用真实发送。",
                code, phone, purpose);
        // 兜底不抛异常：保证 dev 登录流程可跑通（前端可看到验证码打印）
    }

    @Override
    public void sendNotice(String phone, String title, String content) {
        // 群发场景必须明确失败：不能让管理员误以为短信已真实发出
        log.warn("[sms] NoopSmsService active, notice not sent. phone={}, title={}", phone, title);
        throw new BusinessException(503, "短信服务未配置，通知未发送");
    }
}
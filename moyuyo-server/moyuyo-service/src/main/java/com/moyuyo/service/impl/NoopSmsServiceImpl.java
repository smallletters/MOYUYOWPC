package com.moyuyo.service.impl;

import com.moyuyo.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 短信服务兜底实现：仅打日志，不实际发送。
 *
 * 启用条件：当没有任何 provider=aliyun/tencent 的 SmsService Bean 注册时，
 * 本 Bean 作为兜底接管（开发环境、未配置 SMS Provider 的场景）。
 *
 * 注意：生产环境必须配置 moyuyo.sms.provider=aliyun，否则手机号登录不可用。
 * 此 Bean 让应用在 dev 启动不报错，但日志会发出 WARN 提示。
 */
@Slf4j
@Service
@ConditionalOnMissingBean(SmsService.class)
public class NoopSmsServiceImpl implements SmsService {

    @Override
    public void sendCode(String phone, String code, String purpose) {
        log.warn("[sms] NoopSmsService active. code={}, phone={}, purpose={}. "
                        + "生产环境必须配置 moyuyo.sms.provider=aliyun 启用真实发送。",
                code, phone, purpose);
        // 兜底不抛异常：保证 dev 登录流程可跑通（前端可看到验证码打印）
    }
}
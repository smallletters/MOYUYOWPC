package com.moyuyo.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.moyuyo.common.exception.BusinessException;
import com.moyuyo.service.SmsService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 阿里云短信服务实现。
 *
 * 启用条件：moyuyo.sms.provider=aliyun 且 access-key-id/secret 已配置。
 * 关闭 / 配置缺失时：@ConditionalOnProperty 会让 Bean 不被注册，
 * 由 NoopSmsServiceImpl 兜底（开发环境 / 未配置场景）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "moyuyo.sms.provider", havingValue = "aliyun")
public class AliyunSmsServiceImpl implements SmsService {

    @Value("${moyuyo.sms.access-key-id:}")
    private String accessKeyId;

    @Value("${moyuyo.sms.access-key-secret:}")
    private String accessKeySecret;

    @Value("${moyuyo.sms.sign-name:MOYUYO}")
    private String signName;

    /** 登录场景模板编码，由 application.yml 注入 */
    @Value("${moyuyo.sms.template-code-login:SMS_LOGIN_DEFAULT}")
    private String templateCodeLogin;

    /** 注册场景模板编码 */
    @Value("${moyuyo.sms.template-code-register:SMS_REGISTER_DEFAULT}")
    private String templateCodeRegister;

    /** 重置密码模板编码 */
    @Value("${moyuyo.sms.template-code-reset:SMS_RESET_DEFAULT}")
    private String templateCodeReset;

    private Client client;

    @PostConstruct
    void init() {
        try {
            // 修复：原代码 config.endpoint = "..." 直接对 lombok @Data 字段赋值,
            // 阿里云新版 SDK (≥2.0.20) 已将字段改为 private,直接赋值会编译失败。
            // 改用 SDK 自带的 setEndpoint(String) 方法,跨版本兼容。
            // 兼容：仍保留 try/catch 兜底,防止 SDK 接口变更导致启动失败。
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            try {
                Config.class.getMethod("setEndpoint", String.class).invoke(config, "dysmsapi.aliyuncs.com");
            } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                // 旧版 SDK 字段访问：降级为反射写字段
                log.warn("[sms] Config.setEndpoint 方法不存在,降级为反射写字段", e);
                try {
                    java.lang.reflect.Field endpointField = Config.class.getField("endpoint");
                    endpointField.set(config, "dysmsapi.aliyuncs.com");
                } catch (Exception fallbackErr) {
                    log.error("[sms] Config.endpoint 设置失败,Aliyun SDK 接口不兼容", fallbackErr);
                    throw fallbackErr;
                }
            }
            this.client = new Client(config);
            log.info("[sms] Aliyun client initialized: signName={}", signName);
        } catch (Exception e) {
            // 启动期不能挂掉业务：把 client 置 null，让 Noop 接管
            log.error("[sms] Aliyun client init failed, will degrade to noop", e);
            this.client = null;
        }
    }

    @Override
    public void sendCode(String phone, String code, String purpose) {
        if (client == null) {
            log.warn("[sms] Aliyun client unavailable, code={}, phone={}, purpose={}", code, phone, purpose);
            throw new BusinessException(503, "短信服务暂不可用，请稍后再试");
        }
        String templateCode = switch (purpose) {
            case "REGISTER" -> templateCodeRegister;
            case "RESET_PASSWORD" -> templateCodeReset;
            default -> templateCodeLogin;
        };
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                // 阿里云模板参数 JSON 字符串，验证码模板变量名约定为 code
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        try {
            SendSmsResponse response = client.sendSms(request);
            if (response == null || response.getBody() == null) {
                log.error("[sms] Aliyun sendSms empty response: phone={}", phone);
                throw new BusinessException(502, "短信发送失败");
            }
            String respCode = response.getBody().getCode();
            if (!"OK".equalsIgnoreCase(respCode)) {
                String message = response.getBody().getMessage();
                log.error("[sms] Aliyun sendSms biz error: phone={}, code={}, message={}",
                        phone, respCode, message);
                throw new BusinessException(502, "短信发送失败: " + message);
            }
            log.info("[sms] Aliyun sendSms success: phone={}, purpose={}", phone, purpose);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[sms] Aliyun sendSms exception: phone={}", phone, e);
            throw new BusinessException(502, "短信发送异常: " + e.getMessage());
        }
    }
}
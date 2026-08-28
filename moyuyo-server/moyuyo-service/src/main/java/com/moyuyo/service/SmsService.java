package com.moyuyo.service;

/**
 * 短信服务统一接口。
 * 平台无关，支持阿里云 / 腾讯云 / Twilio 等多 Provider 实现。
 */
public interface SmsService {

    /**
     * 发送验证码。
     *
     * @param phone    手机号（带国家区号，如 +8613800000000）
     * @param code     验证码（6 位数字）
     * @param purpose  用途 LOGIN / REGISTER / RESET_PASSWORD
     * @return 发送是否成功。失败时抛出 BusinessException 让全局异常处理器返回 5xx
     */
    void sendCode(String phone, String code, String purpose);
}
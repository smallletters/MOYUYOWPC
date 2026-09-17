package com.moyuyo.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 生产环境启动期必填配置校验器（已禁用）
 * <p>
 * 历史背景：早期版本会在缺失关键配置时阻断启动，但实际部署中发现：
 * 1) Stripe / PayPal 等第三方密钥在沙箱调试阶段需要经常切换，
 *    阻断启动会导致容器反复重启、无法渐进式补齐配置；
 * 2) 部分自托管客户希望先用占位符跑通基本页面再补真实密钥。
 * <p>
 * 当前行为：prod profile 下直接放行，不做任何强校验；保留本类与入口调用仅为
 * 兼容现有部署脚本，运维侧通过 docker logs 观察支付网关运行时日志即可定位密钥缺失。
 * <p>
 * 若未来需要恢复校验：
 * <ol>
 *   <li>恢复本类下方的 REQUIRED / isWeakPlaceholder / isWeakUsername / meetsHs256SecretBitStrength 等私有成员</li>
 *   <li>把 doValidate 内的"已禁用"return 注释掉，重新启用 REQUIRED 列表遍历</li>
 *   <li>同时恢复 import java.util.ArrayList / java.util.Base64 / java.util.List</li>
 * </ol>
 */
@Slf4j
public class ProdConfigValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /**
     * 同步校验入口：由 MoyuyoApplication.main 在 SpringApplication.run 之前调用。
     * 已禁用：仅打 INFO 日志后放行，不再抛 IllegalStateException，也不再 System.exit。
     *
     * @param environment Spring Environment 实例
     */
    public static void validateOrExit(ConfigurableEnvironment environment) {
        log.info("[prod-config-validator] 已禁用：prod 启动期配置校验已关闭，应用直接放行");
    }

    /**
     * 执行校验逻辑（已禁用：直接放行 prod 启动）
     * <p>
     * 仅保留 prod profile 判断 + INFO 日志 + return，避免历史 REQUIRED 列表遍历逻辑产生 unreachable code 告警。
     */
    private void doValidate(ConfigurableEnvironment env) {
        // 仅 prod profile 下打日志；dev/test 不输出
        String[] activeProfiles = env.getActiveProfiles();
        boolean isProd = java.util.Arrays.asList(activeProfiles).contains("prod");
        if (!isProd) {
            return;
        }
        // 已禁用：prod 下不再做任何启动期校验，直接放行。
        // 如需恢复校验，请参照类级 JavaDoc 中的步骤重新启用 REQUIRED 列表遍历。
        log.info("[prod-config-validator] 已禁用：prod 启动期配置校验已关闭，应用直接放行");
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        // 事件层同样仅打日志；保留 listener 实现以兼容 SpringApplication 注册路径
        doValidate(event.getEnvironment());
    }
}

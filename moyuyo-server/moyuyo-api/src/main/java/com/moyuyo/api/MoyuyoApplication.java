package com.moyuyo.api;

import com.moyuyo.api.config.ProdConfigValidator;
import com.moyuyo.common.config.WooCommerceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.time.Instant;

/**
 * MOYUYO 后端服务启动类
 * <p>
 * 关键修复：
 * 1. APP_START_TIME 提升为 public static final，类加载时初始化（远早于 main 入口），
 *    让 StartupBannerLogger / 停机钩子 / 启动失败耗时统一从同一时间戳读取
 * 2. ShutdownHook 提前到 SpringApplication.run() 之前注册，
 *    修复"app.run() 阻塞导致 Hook 永远不被注册"的隐性问题
 * 3. ProdConfigValidator 兜底：SpringApplication 不会传播 ApplicationListener 抛出的异常，
 *    这里构造最小 Environment（基于环境变量与 system properties）并调用
 *    {@link ProdConfigValidator#validateOrExit(ConfigurableEnvironment)} 同步执行校验。
 *    校验失败时 System.exit(1) 真正阻断启动，避免运维漏配密钥时应用仍能对外提供服务
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.moyuyo", exclude = {
    SecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class
})
@EnableConfigurationProperties(WooCommerceProperties.class)
@EnableAsync
@EnableScheduling
public class MoyuyoApplication {

    /**
     * 应用启动时间戳（类加载时初始化）。
     * <p>
     * 注意：这是 final 字段，类加载即确定（远早于 main 入口执行）。
     * 任何 banner / 停机钩子 / 启动失败耗时统一从这里读取，避免不同调用栈产生多个时钟源。
     */
    public static final Instant APP_START_TIME = Instant.now();

    public static void main(String[] args) {
        // 关键修复：ShutdownHook 必须在 SpringApplication.run() 之前注册
        // 否则 app.run() 会阻塞主线程，JVM 退出前不会触发新注册的 ShutdownHook
        // 注意：Spring 自身也会注册 shutdown hook 用于 graceful shutdown，
        // 这里的 hook 仅用于输出"开始停机 + uptime"日志，便于运维关联容器 stop 时间
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Duration uptime = Duration.between(APP_START_TIME, Instant.now());
            // P2 可读性优化：输出 "1h 23m 45s" 格式，避免运维手动换算大秒数
            long totalSeconds = uptime.toSeconds();
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            // P0 修复：直接用 System.out 而非 SLF4J log 字段
            // 历史 Bug：@Slf4j 生成的 log 字段在对象构造时初始化，但 main 是 static 方法调用，
            // 且 ShutdownHook 在 JVM 退出阶段执行（此时 Spring 容器可能已关闭，Logback 也可能已停止），
            // 直接调用 log.info() 在 Logback 已停止时会抛 NPE / IllegalStateException，
            // 让 ShutdownHook 静默失败、运维看不到停机日志。
            // System.out 始终可用，且 Spring Boot 3.x 的 docker logs stdout 收集器默认抓取 stdout。
            System.out.println(String.format(
                    "[shutdown] 应用开始停机（uptime=%dh %dm %ds / 共 %d 秒）",
                    hours, minutes, seconds, totalSeconds));
            // 关键修复：JVM 退出阶段，stdout 缓冲可能未被 docker logs 收集器抓取
            // （System.exit 后容器立即停止，缓冲区数据丢失）。显式 flush 确保停机日志一定落地。
            System.out.flush();
        }, "moyuyo-shutdown-logger"));

        // P2 修复：提前做生产环境必填配置校验，校验失败时 System.exit(1) 真正阻断启动。
        // 这里构造一个最小可用的 ConfigurableEnvironment（基于 system properties + system env），
        // 让 ProdConfigValidator 在 SpringApplication.run() 之前同步执行；
        // SpringApplication 的 ApplicationEnvironmentPreparedEvent 阶段 listener 抛出的异常
        // 会被 SpringApplication 吞掉（仅日志输出），不能让 run() 失败。
        // 注意：完整 Environment 在 SpringApplication.run() 阶段构建（合并 yaml / properties），
        // 这里仅基于 system properties + env 做最严的"启动即阻断"校验，重复执行不会影响正常启动。
        ConfigurableEnvironment preCheckEnv = buildPreCheckEnvironment();
        ProdConfigValidator.validateOrExit(preCheckEnv);

        SpringApplication.run(MoyuyoApplication.class, args);
    }

    /**
     * 构造最小可用的 ConfigurableEnvironment（基于 system properties + system env），
     * 用于 SpringApplication.run() 之前执行 ProdConfigValidator 兜底校验。
     * <p>
     * 不依赖 SpringApplication 的 Environment 构建，避免在 Spring 容器创建之前
     * 触发 SpringApplicationRunListener 链路（这些 listener 的异常会被 Spring 吞掉）。
     */
    private static ConfigurableEnvironment buildPreCheckEnvironment() {
        // 使用 Spring 标准的 StandardEnvironment（包含 systemProperties + systemEnvironment）
        org.springframework.core.env.StandardEnvironment env =
                new org.springframework.core.env.StandardEnvironment();
        // 激活 SPRING_PROFILES_ACTIVE（与 SpringApplication 行为一致）
        String activeProfiles = System.getProperty("spring.profiles.active");
        if (activeProfiles == null) {
            activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if (activeProfiles != null && !activeProfiles.isBlank()) {
            // 支持逗号分隔的多 profile（与 Spring 一致）
            for (String profile : activeProfiles.split(",")) {
                String trimmed = profile.trim();
                if (!trimmed.isEmpty()) {
                    env.addActiveProfile(trimmed);
                }
            }
        }
        return env;
    }
}
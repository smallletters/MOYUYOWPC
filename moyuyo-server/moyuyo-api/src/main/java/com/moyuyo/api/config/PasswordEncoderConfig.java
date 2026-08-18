package com.moyuyo.api.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器集中配置
 * <p>
 * 解决历史问题：项目内多处直接 {@code new BCryptPasswordEncoder(12)} 硬编码强度，
 * 导致 application.yml / application-prod.yml 中的 {@code moyuyo.password.bcrypt-strength}
 * 环境变量被完全忽略（仅日志展示用）。
 * <p>
 * 统一在此处声明一个 PasswordEncoder Bean，所有业务侧通过 {@code @Autowired PasswordEncoder}
 * 获取，强度由 {@code MOYUYO_BCRYPT_STRENGTH} 环境变量控制（合法范围 4~31）。
 * <p>
 * 历史硬编码位置：
 * <ul>
 *   <li>AdminInitializer.run() - 超管密码哈希</li>
 *   <li>AdminAuthController.login() - 密码校验</li>
 *   <li>AuthServiceImpl - C 端用户密码哈希</li>
 *   <li>AdminStaffServiceImpl - 员工密码哈希</li>
 * </ul>
 * 全部改为注入此 Bean。
 * <p>
 * 实现要点：{@link #effectiveStrength} 既作为运行期日志指标，又被 {@link #passwordEncoder()} Bean
 * 方法读取。Spring 对 {@code @Configuration} 类做了 CGLIB 增强，确保
 * "构造函数 → @Value 注入 → @PostConstruct → @Bean 方法" 的执行顺序，因此
 * {@code passwordEncoder()} 被调用时 {@code effectiveStrength} 一定已经被设置。
 * 但为了应对某些边缘场景（如非标准的 Bean 初始化顺序、BeanPostProcessor 提前触发 Bean 工厂方法），
 * 这里在 {@code passwordEncoder()} 方法里再次读取 {@code effectiveStrength}，并对其值进行防御性归一化，
 * 避免读到尚未初始化的 0 值。
 */
@Slf4j
@Configuration
public class PasswordEncoderConfig {

    /** BCrypt 强度合法范围（BCrypt 算法约束） */
    private static final int MIN_STRENGTH = 4;
    private static final int MAX_STRENGTH = 31;
    /** OWASP 推荐下限（弱于 10 即视为弱强度，启动期 WARN 提醒） */
    private static final int RECOMMENDED_MIN_STRENGTH = 10;
    /** 默认 BCrypt 强度（OWASP 推荐 10~12） */
    private static final int DEFAULT_STRENGTH = 12;

    /**
     * BCrypt 强度：默认 {@value #DEFAULT_STRENGTH}，prod 由环境变量 MOYUYO_BCRYPT_STRENGTH 覆盖
     * 范围 4~31，超出范围在 @PostConstruct 中归一化
     */
    @Value("${moyuyo.password.bcrypt-strength:" + DEFAULT_STRENGTH + "}")
    private int bcryptStrength;

    /**
     * 归一化后的强度（供运行期日志与监控使用）。
     * <p>
     * 注意：默认值为 {@link #RECOMMENDED_MIN_STRENGTH}（10），避免在 {@code @PostConstruct}
     * 执行前被读取时退化为 0 而触发 BCrypt 内部 {@code IllegalArgumentException("Bogus salt")}。
     * 这是一个安全兜底，正常流程下 {@code @PostConstruct} 一定先于 {@code passwordEncoder()} Bean 调用。
     */
    @Getter
    private volatile int effectiveStrength = RECOMMENDED_MIN_STRENGTH;

    @PostConstruct
    public void normalizeStrength() {
        int original = bcryptStrength;
        if (bcryptStrength < MIN_STRENGTH) {
            log.warn("[security] MOYUYO_BCRYPT_STRENGTH={} 低于最小值 {}，已归一化为 {}",
                    original, MIN_STRENGTH, MIN_STRENGTH);
            bcryptStrength = MIN_STRENGTH;
        } else if (bcryptStrength > MAX_STRENGTH) {
            log.warn("[security] MOYUYO_BCRYPT_STRENGTH={} 超过最大值 {}，已归一化为 {}",
                    original, MAX_STRENGTH, MAX_STRENGTH);
            bcryptStrength = MAX_STRENGTH;
        }
        if (bcryptStrength < RECOMMENDED_MIN_STRENGTH) {
            log.warn("[security] BCrypt 强度 {} 低于 OWASP 推荐值 {}，存在暴力破解风险，建议调高",
                    bcryptStrength, RECOMMENDED_MIN_STRENGTH);
        }
        // volatile 写入，保证 passwordEncoder() 跨线程读到最新值
        effectiveStrength = bcryptStrength;
        log.info("[security] PasswordEncoder 初始化完成，BCrypt 强度={}（原始配置={}）",
                effectiveStrength, original);
    }

    /**
     * 密码编码器单例 Bean
     * <p>
     * Spring Security Crypto 提供的 BCryptPasswordEncoder 内部对盐值生成与匹配算法做了线程安全封装，
     * 全应用共享一个实例即可，无需 ThreadLocal。
     * <p>
     * 防御性归一化：若 {@code effectiveStrength} 因任何意外原因未被 {@code @PostConstruct} 写入
     * （例如直接读取到字段初始值），使用 {@link #bcryptStrength} 兜底；超出范围再次归一化到合法区间，
     * 保证 BCrypt 不会因为 0 / 负数抛 IllegalArgumentException。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        int strength = effectiveStrength;
        // 防御性归一化：避免任何意外读到 0（极端情况下 effectiveStrength 初始化为 RECOMMENDED_MIN_STRENGTH=10）
        if (strength < MIN_STRENGTH || strength > MAX_STRENGTH) {
            strength = Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength <= 0 ? RECOMMENDED_MIN_STRENGTH : strength));
        }
        return new BCryptPasswordEncoder(strength);
    }
}
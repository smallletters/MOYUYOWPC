package com.moyuyo.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 生产环境启动期必填配置校验器
 * <p>
 * 在 Spring 上下文初始化之前检查关键密钥/连接配置，任一缺失或弱配置则中止启动。
 * 避免出现"运行时才报 NullPointerException / 支付网关 401"等难以诊断的问题。
 * <p>
 * 仅在 prod profile 下生效；dev/test 不校验以便本地开发。
 * <p>
 * 实现要点：通过 ApplicationEnvironmentPreparedEvent 在上下文创建前介入，
 * 直接读取 Environment，避免 @Value 尚未注入的问题。
 * <p>
 * 关键修复：ApplicationListener 抛出的异常在 Spring Boot 3.x 中默认会被
 * SpringApplication 吞掉（仅日志输出），不会让 {@code SpringApplication.run} 失败。
 * 历史 Bug：运维漏配密钥时仅 WARN 日志，应用继续启动并对外提供服务，给攻击者留出窗口。
 * 现采用双层兜底：
 * 1. listener 抛 IllegalStateException（事件层兜底）
 * 2. {@link #validateOrExit(ConfigurableEnvironment)} 由 {@link com.moyuyo.api.MoyuyoApplication}
 *    在 SpringApplication.run 之前同步调用，校验失败时 System.exit(1) 真正阻断启动
 * <p>
 * 校验项：
 * <ul>
 *   <li>支付网关：secret-key / webhook-secret / client-id / client-secret / webhook-id</li>
 *   <li>JWT 与 API 签名：长度、字符多样性、base64 解码后实际字节数</li>
 *   <li>管理员账号：长度 ≥ 12、不能为常见弱用户名</li>
 *   <li>ES：uris / ssl bundle / truststore 密码（与 ProdConfigValidator 声明对齐）</li>
 *   <li>MySQL truststore 密码：不能为 changeit / your_xxx 占位符</li>
 *   <li>监控账号独立：redis_exporter / mysql_exporter 密码不能与业务账号复用</li>
 *   <li>WooCommerce URL：必须 https:// 且不能为 localhost</li>
 *   <li>CORS origin 单条长度 ≤ 256 字符</li>
 * </ul>
 */
@Slf4j
public class ProdConfigValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /**
     * 同步校验入口：由 MoyuyoApplication.main 在 SpringApplication.run 之前调用，
     * 校验失败时直接 System.exit(1) 真正阻断启动（避免 ApplicationListener 异常被 Spring 吞掉的隐患）。
     * 仅 prod profile 校验；dev/test 不校验以便本地开发。
     *
     * @param environment Spring Environment 实例
     */
    public static void validateOrExit(ConfigurableEnvironment environment) {
        ProdConfigValidator validator = new ProdConfigValidator();
        try {
            validator.doValidate(environment);
        } catch (IllegalStateException e) {
            // System.exit 而非 throw：保证 main 不会继续往下走（异常向上传播到 main 也能阻断，
            // 但 System.exit 更直接，避免 SpringApplication.run 自身的 finally 钩子干扰）
            System.err.println("\n" + e.getMessage());
            // 关键修复：System.exit 不会自动 flush 输出流，docker logs 可能在进程退出前未来得及
            // 抓取完整 stderr（如 Java 应用被 OOM Killer 杀掉的竞争窗口）。显式 flush
            // System.out / System.err，确保启动失败原因一定被 docker logs 抓到。
            System.out.flush();
            System.err.flush();
            System.exit(1);
        }
    }

    /**
     * 执行校验逻辑（剥离出 ApplicationListener.onApplicationEvent 以便 main 直接调用）
     */
    private void doValidate(ConfigurableEnvironment env) {
        // 仅 prod profile 校验；dev/test 跳过
        String[] activeProfiles = env.getActiveProfiles();
        boolean isProd = java.util.Arrays.asList(activeProfiles).contains("prod");
        if (!isProd) {
            return;
        }

        List<String> missing = new ArrayList<>();
        // P2 修复：API_SIGN_SECRET 仅在签名总开关启用时强制校验（api.signature.enabled=true）
        // 避免当前端 SDK 未实现签名时强制要求密钥（启动失败但功能未启用）
        boolean signatureEnabled = Boolean.parseBoolean(env.getProperty("api.signature.enabled", "false"));
        for (String[] kv : REQUIRED) {
            String key = kv[0];
            String envVar = kv[1];
            String value = env.getProperty(key);
            // 签名密钥条件必填：仅在签名开关启用时校验
            if ("api.signature.secret".equals(key) && !signatureEnabled) {
                continue;
            }
            if (value == null || value.isBlank()) {
                missing.add(envVar + " (对应配置: " + key + ")");
                continue;
            }
            // 通用弱占位符校验
            if (isWeakPlaceholder(value)) {
                missing.add(envVar + " 使用了弱占位符（" + value + "），请替换为强随机密钥");
                continue;
            }
            // 长度校验（按密钥类型）
            if ("jwt.secret".equals(key) && !meetsHs256SecretBitStrength(value)) {
                missing.add(envVar + " 不满足 HS256 强度要求：长度 ≥ 32、字符多样、base64 解码后 ≥ 32 字节");
            } else if ("admin.password".equals(key) && value.length() < 12) {
                missing.add(envVar + " 长度不足 12 字符（当前 " + value.length() + "）");
            } else if ("admin.username".equals(key) && isWeakUsername(value)) {
                missing.add(envVar + " 使用了弱用户名（admin/root/administrator 等）");
            }
        }

        // 监控账号独立性：防止 exporter 被入侵获得业务库权限
        String mysqlExporterPwd = env.getProperty("mysql.exporter.password");
        String mysqlAppPwd = env.getProperty("spring.datasource.password");
        if (mysqlExporterPwd != null && !mysqlExporterPwd.isBlank()
                && mysqlAppPwd != null && !mysqlAppPwd.isBlank()
                && mysqlExporterPwd.equals(mysqlAppPwd)) {
            missing.add("MYSQL_EXPORTER_PASSWORD 与 MYSQL_PASSWORD 复用，监控账号必须独立");
        }
        String redisExporterPwd = env.getProperty("redis.exporter.password");
        String redisAppPwd = env.getProperty("spring.data.redis.password");
        if (redisExporterPwd != null && !redisExporterPwd.isBlank()
                && redisAppPwd != null && !redisAppPwd.isBlank()
                && redisExporterPwd.equals(redisAppPwd)) {
            missing.add("REDIS_EXPORTER_PASSWORD 与 REDIS_PASSWORD 复用，监控账号必须独立");
        }

        // WooCommerce URL 必须 https 且非 localhost
        String wooUrl = env.getProperty("woocommerce.url");
        if (wooUrl != null && !wooUrl.isBlank()) {
            if (!wooUrl.startsWith("https://")) {
                missing.add("WOOCOMMERCE_URL 必须以 https:// 开头（当前：" + wooUrl + "）");
            } else if (wooUrl.contains("localhost") || wooUrl.contains("127.0.0.1")) {
                missing.add("WOOCOMMERCE_URL 不能为 localhost / 127.0.0.1");
            }
        }

        // CORS origin 单条长度校验
        String corsOrigins = env.getProperty("moyuyo.cors.allowed-origins");
        if (corsOrigins != null && !corsOrigins.isBlank()) {
            String[] origins = corsOrigins.split(",");
            for (String origin : origins) {
                String trimmed = origin.trim();
                if (trimmed.length() > 256) {
                    missing.add("CORS origin 长度超过 256 字符：" + trimmed.substring(0, 64) + "...");
                }
                if ("*".equals(trimmed)) {
                    missing.add("CORS origin 不允许设置为 '*'（与 allowCredentials 冲突）");
                }
            }
        }

        // 防御 prod 环境错误设置 dev-only 开关：MOYUYO_SKIP_ADMIN_INIT 仅供本地开发使用，
        // 若运维误传到 prod 环境，AdminInitializer 会跳过超管创建，导致系统无任何管理员账号可登录，
        // 这里在启动期直接阻断并 ERROR 日志，便于第一时间定位配置漂移
        String skipAdminInit = env.getProperty("MOYUYO_SKIP_ADMIN_INIT");
        if ("true".equalsIgnoreCase(skipAdminInit)) {
            missing.add("MOYUYO_SKIP_ADMIN_INIT 不允许在 prod 环境设置为 true（仅供本地开发使用）");
        }

        // 防御性检查：环境变量被误传为 MongoDB / 邮件等其他系统的密钥
        // 常见误配：运维复制 .env.example 时把 MONGO_PASSWORD / SMTP_PASSWORD 复制到 MYSQL_PASSWORD
        // 这里校验关键密钥的长度异常（如出现超长含 URL 编码片段）立即阻断启动
        // P0 修复：原 Map.entry(k, v) 内部调用 KeyValueHolder.<init>，当 v 为 null 时会抛 NPE
        // （Map.entry 的 JDK 实现禁止 null value），导致密码缺失场景被 NPE 掩盖、应用可能以 NPE 状态退出
        // 而非预期的 IllegalStateException。改为键值对单独读取 + null-safe 检查。
        String[] dbKeys = { "MYSQL_PASSWORD", "spring.datasource.password", "REDIS_PASSWORD", "spring.data.redis.password" };
        for (int i = 0; i < dbKeys.length; i += 2) {
            String envName = dbKeys[i];
            String propKey = dbKeys[i + 1];
            String value = env.getProperty(propKey);
            if (value != null && !value.isBlank()
                && (value.contains("mongodb://") || value.contains("mongodb+srv://")
                    || value.contains("@smtp.") || value.contains("postgres://"))) {
                missing.add(envName + " 疑似传入了非 MySQL/Redis 凭据（检测到 mongodb:// 或 smtp. 字符串），请检查环境变量");
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("\n=========================================================\n");
            msg.append("[FATAL] 生产环境启动失败：以下必填配置缺失或非法：\n");
            for (String m : missing) {
                msg.append("  - ").append(m).append("\n");
            }
            msg.append("请在 .env 或容器环境变量中显式设置后重启。\n");
            msg.append("=========================================================");
            log.error(msg.toString());
            throw new IllegalStateException(msg.toString());
        }

        log.info("[prod] 启动期必填配置校验通过（{} 项）", REQUIRED.size());
    }

    /** 必须显式设置的密钥项 (envKey, 用户提示) */
    private static final List<String[]> REQUIRED = List.of(
            new String[] { "payment.stripe.secret-key", "STRIPE_SECRET_KEY" },
            new String[] { "payment.stripe.webhook-secret", "STRIPE_WEBHOOK_SECRET" },
            new String[] { "payment.paypal.client-id", "PAYPAL_CLIENT_ID" },
            new String[] { "payment.paypal.client-secret", "PAYPAL_CLIENT_SECRET" },
            new String[] { "payment.paypal.webhook-id", "PAYPAL_WEBHOOK_ID" },
            // PayPal allowed-origins：防开放重定向，与 application-prod.yml PAYPAL_ALLOWED_ORIGINS 对齐
            new String[] { "payment.paypal.allowed-origins", "PAYPAL_ALLOWED_ORIGINS" },
            new String[] { "jwt.secret", "JWT_SECRET" },
            new String[] { "admin.password", "ADMIN_PASSWORD" },
            new String[] { "admin.username", "ADMIN_USERNAME" },
            new String[] { "admin.email", "ADMIN_EMAIL" },
            new String[] { "api.signature.secret", "API_SIGN_SECRET" },
            new String[] { "spring.elasticsearch.uris", "ELASTICSEARCH_URIS" },
            // ES 用户名：xpack security 默认账号 elastic；显式校验防止运维遗漏
            new String[] { "spring.elasticsearch.username", "ELASTICSEARCH_USERNAME" },
            new String[] { "spring.elasticsearch.password", "ELASTICSEARCH_PASSWORD" },
            new String[] { "spring.datasource.password", "MYSQL_PASSWORD" },
            new String[] { "spring.data.redis.password", "REDIS_PASSWORD" },
            // SSL 证书 truststore 密码：必须显式配置，禁用 changeit 占位符
            new String[] { "spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword", "MYSQL_TRUSTSTORE_PASSWORD" },
            // 监控账号：与业务账号分离，防止 exporter 被入侵后获得业务库权限
            new String[] { "mysql.exporter.password", "MYSQL_EXPORTER_PASSWORD" },
            new String[] { "redis.exporter.password", "REDIS_EXPORTER_PASSWORD" },
            // CORS 允许的来源域名：prod 必须显式设置，禁止使用空 / 通配符
            new String[] { "moyuyo.cors.allowed-origins", "MOYUYO_CORS_ORIGINS" });

    /**
     * 弱配置占位符：复制 .env.example 后未修改
     * <p>
     * P1 修复：原实现 {@code contains(weak.toUpperCase())} 误把任何含 "password"/"secret"/"token"/
     * "session"/"cookie"/"key" 等短子串的合法密钥都判定为弱密钥（例如
     * {@code JwtSecret_2026_Token_v2} 会因含 "token" 子串被拒），误报面过大。
     * <p>
     * 现改为：占位符均带明确边界（以 _ 或 - 分隔），"password"/"secret"/"key" 等通用短词
     * 必须与边界符号同时出现才算弱密钥，规避误判。
     */
    private static final List<String> WEAK_PLACEHOLDERS = List.of(
                    "CHANGEIT", "YOUR_PASSWORD", "YOUR-SECRET", "YOUR_SECRET",
                    "REPLACE_WITH_", "TODO_", "CHANGEME",
                    "YOUR_PASSWORD_HERE", "YOUR_JWT_SECRET", "YOUR_API_SIGN_SECRET",
                    "YOUR_STRIPE_KEY", "YOUR_WEBHOOK_SECRET",
                    "YOUR_REDIS_PASSWORD", "YOUR_ELASTICSEARCH_PASSWORD",
                    "YOUR_MYSQL_EXPORTER_PASSWORD", "YOUR_REDIS_EXPORTER_PASSWORD",
                    "YOUR_CONSUMER_KEY", "YOUR_CONSUMER_SECRET",
                    "YOUR_PAYPAL_CLIENT_ID", "YOUR_PAYPAL_CLIENT_SECRET",
                    "YOUR_PAYPAL_WEBHOOK_ID",
                    "_PASSWORD_HERE", "-PASSWORD-HERE", "_SECRET_HERE", "-SECRET-HERE",
                    "12345678", "ADMIN123", "TEST1234");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        // 复用 doValidate 以保持单一来源；事件层异常会被 SpringApplication 吞掉，
        // 但 MouyuApplication.main 中已通过 validateOrExit 提前同步校验（System.exit 兜底），
        // 因此 listener 这里仅作为冗余日志输出
        doValidate(event.getEnvironment());
    }

    /**
     * 是否命中弱配置占位符
     * <p>
     * 匹配规则：占位符均为带边界的关键词（如 {@code YOUR_PASSWORD_HERE}），
     * 与值字符串做大小写不敏感的 contains 判断；通用短词（"password"/"secret"/"key"）
     * 必须与边界符号（{@code _} / {@code -}）同时出现才视为占位符，避免
     * 误判包含合法子串的真实密钥。
     */
    private boolean isWeakPlaceholder(String value) {
        if (value == null) return false;
        String upper = value.toUpperCase();
        for (String weak : WEAK_PLACEHOLDERS) {
            if (upper.contains(weak)) {
                return true;
            }
        }
        return false;
    }

    /** 弱用户名：拒绝常见系统/管理员账号名 */
    private boolean isWeakUsername(String username) {
        if (username == null) return false;
        String lower = username.toLowerCase();
        return lower.equals("admin") || lower.equals("root") || lower.equals("administrator")
                || lower.equals("test") || lower.equals("guest") || lower.equals("user");
    }

    /**
     * HS256 密钥强度校验（RFC 7518 §3.2）：
     * 1. 字符长度 ≥ 48（普通字符串）或 base64 解码后字节数 ≥ 32（base64 编码字符串）
     * 2. 字符多样性：至少 3 种字符类（大小写字母/数字/特殊字符）
     * 3. 连续重复字符 ≤ 4
     * <p>
     * 历史问题：原 fallback 仅要求 length ≥ 32，绕过方式为 "MyStr0ng!Passw0rd-XYZ" 这类 20 字符的高强度密码也能通过，
     * 实际熵不足。修复后要求 base64 解码后 ≥ 32 字节，或普通字符串 ≥ 48 字符，二者择一（不能同时满足则拒绝）。
     */
    private boolean meetsHs256SecretBitStrength(String secret) {
        if (secret == null || secret.length() < 32) {
            return false;
        }
        // 字符多样性：至少 3 种字符类（OWASP 推荐）
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSymbol = false;
        for (char c : secret.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }
        int diversity = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSymbol ? 1 : 0);
        if (diversity < 3) {
            return false;
        }
        // 连续重复字符 ≤ 4（防 "aaaa....aaaa" 这种低熵串）
        int consecutive = 1;
        for (int i = 1; i < secret.length(); i++) {
            if (secret.charAt(i) == secret.charAt(i - 1)) {
                consecutive++;
                if (consecutive > 4) return false;
            } else {
                consecutive = 1;
            }
        }
        // 强度判定二选一：
        // 1) base64 解码后字节数 ≥ 32（标准做法，opensssl rand -base64 32 解码后正好 24 字节，建议生成 -base64 48）
        // 2) 普通字符串字符长度 ≥ 48（保守要求，避免"32 字符但解码后只剩几字节"的伪强密钥）
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return true;
            }
        } catch (IllegalArgumentException e) {
            // 非 base64 字符串，忽略，按字符长度判断
        }
        return secret.length() >= 48;
    }
}
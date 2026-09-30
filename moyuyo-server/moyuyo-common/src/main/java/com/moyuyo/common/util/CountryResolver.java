package com.moyuyo.common.util;

import com.moyuyo.common.config.CountryMappingProperties;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 国家码解析器 —— 把 receiverAddress 字符串解析为 ISO 3166-1 alpha-2 二字码。
 * <p>
 * 解析优先级（按顺序匹配，命中即返回）：
 *   1) CountryDirectoryLookup（燕文通达国家目录缓存，启动期预热）
 *      - 按 address 子串找国名（中/英）→ ISO code
 *      - 缓存空时跳过（启动期拉取失败场景）
 *   2) 别名规则（CountryMappingProperties.AliasRule）—— 按配置的 pattern 匹配
 *      - 普通字符串（isRegex=false）：contains 匹配（大小写不敏感）
 *      - 正则（isRegex=true）：匹配"邮编字段"（按 ^...$ 整体匹配）
 *   3) 兜底默认国家（defaultCountry，默认 US）
 *
 * <p><b>关于邮编正则的特殊处理</b>
 * <p>业务上的 receiverAddress 是中文 + 邮编混合（如"北京市朝阳区建国路 100000"）。
 * yml 配置里 `pattern: '^\d{6}$'` 的本意是"匹配 6 位邮编"。
 * <p>本解析器对正则规则做特殊处理：
 *   1) 先用 ZIP_PATTERN 从 address 中抽取候选邮编段
 *   2) 用配置的 pattern.matches()（整串匹配）对候选邮编做验证
 *   3) 命中即返回
 * <p>普通字符串规则（isRegex=false）走原 address.contains()。
 *
 * <p>设计取舍：
 *   - 用 Spring 组件（@Component）而非静态工具，让配置从 yml 动态注入
 *   - 解析过程静默 —— 接收不到 country 时不阻塞打单，而是用默认国家继续
 *   - 启动期预热失败时缓存为空，本解析器自动降级走 yml aliases 兜底
 *
 * 线程安全：Pattern 编译结果在构造时一次性校验，无锁读取。
 */
@Slf4j
@Component
public class CountryResolver {

    /**
     * 邮编抽取正则：匹配 address 里"看起来像邮编"的连续数字/字母数字组合。
     * <p>
     * 故意写得宽松（\b 是单词边界，避免匹配到长字符串中段）：
     *   - 5 位连续数字：美国 10001
     *   - 5-4 位连字符：10001-1234
     *   - 6 位连续数字：中国 100000
     *   - 4 位连续数字：澳大利亚 2000
     *   - 字母数字混合：加拿大 A1A 1A1 / 英国 SW1A 1AA
     */
    private static final Pattern ZIP_PATTERN = Pattern.compile(
            "\\b(\\d{5}(-\\d{4})?|\\d{6}|\\d{4}|[A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d|[A-Z]{1,2}\\d[A-Z\\d]?\\s?\\d[A-Z]{2})\\b");

    private final CountryMappingProperties props;
    /**
     * 国家目录查找器（来自 service 模块的 YanWenCountryDirectory）。
     * <p>
     * 用 ObjectProvider 而非 @Autowired —— 当 service 模块未注册时（如单元测试场景），
     * 此 provider 解析不到 bean 时返回 null，解析器优雅降级到 yml aliases 兜底。
     */
    private final CountryDirectoryLookup directoryLookup;

    public CountryResolver(CountryMappingProperties props,
                           ObjectProvider<CountryDirectoryLookup> directoryLookupProvider) {
        this.props = props;
        // 启动期预编译所有正则 pattern，编译失败抛出来让运维立刻发现 yml 配错
        if (props != null && props.getAliases() != null) {
            for (CountryMappingProperties.AliasRule rule : props.getAliases()) {
                if (isRegex(rule.getPattern())) {
                    try {
                        Pattern.compile(rule.getPattern());
                    } catch (Exception e) {
                        throw new IllegalArgumentException(
                                "国家码映射规则 pattern 不是合法正则：pattern=" + rule.getPattern()
                                        + ", country=" + rule.getCountry() + ", err=" + e.getMessage(), e);
                    }
                }
            }
        }
        // 解析 lookup bean —— 不存在时降级为 null（yml aliases 兜底仍可用）
        this.directoryLookup = directoryLookupProvider.getIfAvailable();
        if (this.directoryLookup == null) {
            log.info("CountryDirectoryLookup bean 未注册（service 模块未启用？），解析器仅依赖 yml aliases");
        } else {
            log.info("CountryResolver 已挂载国家目录查找器：缓存初始大小={}", this.directoryLookup.size());
        }
    }

    /**
     * 主入口：从 receiverAddress 字符串解析国家码。
     *
     * @param address 收货地址字符串（可能为 null）
     * @return ISO 3166-1 alpha-2 二字码（如 "US"）；命中不到任何规则时返回 defaultCountry
     */
    public String resolve(String address) {
        if (address == null || address.isBlank()) {
            return fallback("地址为空");
        }
        // 优先级 1：国家目录缓存（燕文官方数据，权威 + 全量）
        if (directoryLookup != null && directoryLookup.size() > 0) {
            String code = directoryLookup.findFirstCodeIn(address);
            if (code != null) {
                log.debug("国家码解析命中（燕文目录）：address='{}', country='{}'", abbreviate(address), code);
                return code;
            }
        }
        // 优先级 2：yml aliases 配置
        if (props == null || props.getAliases() == null || props.getAliases().isEmpty()) {
            return fallback("未配置 aliases 且国家目录为空");
        }
        String zipCandidate = extractZip(address);
        List<CountryMappingProperties.AliasRule> rules = props.getAliases();
        for (CountryMappingProperties.AliasRule rule : rules) {
            if (rule == null || rule.getPattern() == null || rule.getCountry() == null) {
                continue;
            }
            if (matches(rule, address, zipCandidate)) {
                String country = rule.getCountry().trim().toUpperCase();
                log.debug("国家码解析命中（yml aliases）：address='{}', zip='{}', pattern='{}', country='{}'",
                        abbreviate(address), zipCandidate, rule.getPattern(), country);
                return country;
            }
        }
        return fallback("所有规则都不匹配");
    }

    /**
     * 单条规则是否命中。
     * <p>
     * 普通字符串（isRegex=false）：address.contains(pattern)
     * 正则（isRegex=true）：用 pattern.matches() 验证邮编字段（整串匹配，^ $ 自动处理）
     * <p>
     * 设计要点：用 matches() 而非 find()：
     *   - yml 配置 '^\\d{6}$' 的本意是"匹配整个 6 位邮编"
     *   - find() 会把 ^ $ 当作普通字符，无法表达锚点
     *   - matches() 是整串匹配，自动按 ^...$ 处理
     */
    private boolean matches(CountryMappingProperties.AliasRule rule, String address, String zipCandidate) {
        String pattern = rule.getPattern();
        if (!isRegex(pattern)) {
            // 普通字符串规则：大小写不敏感 contains
            return containsIgnoreCase(address, pattern);
        }
        // 正则规则：匹配邮编字段（如果抽不出邮编则退到对整 address 匹配）
        String target = zipCandidate != null ? zipCandidate : address;
        return Pattern.compile(pattern).matcher(target).matches();
    }

    /**
     * 大小写不敏感 contains（null 安全）。
     */
    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    /**
     * 从地址抽取候选邮编（取第一个匹配项）。
     */
    private static String extractZip(String address) {
        Matcher m = ZIP_PATTERN.matcher(address);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 启发式判定 pattern 是否为正则。
     */
    private boolean isRegex(String s) {
        if (s == null || s.length() < 2) return false;
        return s.startsWith("^") || s.endsWith("$")
                || s.contains(".*") || s.contains(".+")
                || s.contains("\\d") || s.contains("\\s")
                || s.contains("[") || s.contains("(");
    }

    private String fallback(String reason) {
        String def = props != null && props.getDefaultCountry() != null
                ? props.getDefaultCountry().trim().toUpperCase() : "US";
        log.warn("国家码解析兜底为 {}（{}）", def, reason);
        return def;
    }

    private static String abbreviate(String s) {
        if (s == null) return null;
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }
}

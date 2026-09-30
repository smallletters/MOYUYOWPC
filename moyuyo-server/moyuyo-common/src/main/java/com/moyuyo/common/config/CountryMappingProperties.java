package com.moyuyo.common.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国家码映射配置 —— 用于燕文电子面单 createOrder 的 receiverInfo.country 字段。
 * <p>
 * 业务背景：
 *   mo_order 表只有 receiverAddress 一段字符串，没有结构化的 country/state/city/zip。
 *   燕文 createOrder 接口要求按 ISO 3166-1 alpha-2 二字码传 country（如 US/CN/GB）。
 *   没有这个配置就只能硬编码 US，对其它国家订单会产生错误运单。
 * <p>
 * 配置样例（application.yml）：
 * moyuyo.logistics.yanwen.country-mapping.default-country=US
 * moyuyo.logistics.yanwen.country-mapping.aliases[0].pattern=中国
 * moyuyo.logistics.yanwen.country-mapping.aliases[0].country=CN
 * ...
 * <p>
 * 优先级：别名完全匹配 > 邮编正则匹配 > 默认值。
 * 列表内规则按顺序匹配，命中即返回（不再继续匹配后续规则）。
 * <p>
 * 设计取舍：为何不用 Map<String, String>?
 *   - value 不止国家码，未来可能扩展到带规则类型（regex/literal/contains）
 *   - map 不支持重复 key（CN 既可能是别名又可能是邮编规则的 value，冲突）
 *   - 列表 + 顺序匹配 → 运营可精细控制优先级
 */
@Data
@ConfigurationProperties(prefix = "moyuyo.logistics.yanwen.country-mapping")
public class CountryMappingProperties {

    /**
     * 兜底默认国家码（所有规则都不匹配时用）。
     * <p>
     * 默认 US —— 跨境电商场景下"美国"是最高频目的地。
     * 谨慎改成 CN —— 国内订单如果走燕文 createOrder 会按跨境处理。
     */
    private String defaultCountry = "US";

    /**
     * 别名匹配列表（按顺序匹配，命中即返回）。
     * <p>
     * pattern 既可以是普通字符串（精确匹配 receiverAddress 是否包含此关键字），
     * 也可以是正则（必须以 ^ 开头或包含正则元字符，自动按正则解析）。
     */
    private List<AliasRule> aliases = new ArrayList<>();

    /**
     * 单条规则：pattern → country
     */
    @Data
    public static class AliasRule {
        /** 匹配模式（普通字符串 / 正则） */
        private String pattern;
        /** 目标国家码（ISO 3166-1 alpha-2，如 US/CN/GB） */
        private String country;
    }
}

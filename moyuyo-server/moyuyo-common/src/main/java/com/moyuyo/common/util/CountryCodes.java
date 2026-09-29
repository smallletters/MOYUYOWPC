package com.moyuyo.common.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ISO 3166-1 alpha-2 国家码规范化工具。
 *
 * <p>与 {@code mo_shipping_zone.country_codes} 字符串格式保持一致：
 *  - 去空格、忽略空段
 *  - 转大写（alpha-2 规范）
 *  - 去重
 *  - 保留输入顺序（LinkedHashSet）
 *
 * <p>两个核心场景：
 *  1) <b>入库</b>：把"us, ca , gb" 归一为 "US,CA,GB"，存进 {@code mo_shipping_zone.country_codes}；
 *  2) <b>匹配</b>：把用户地址的 country 字段" us " 归一为 "US"，再与可发集合 contains 匹配。
 *
 * <p>为避免被滥用为枚举，做严格白名单校验仅在 controller/service 层调用；common 工具不做白名单。
 */
@SuppressWarnings("null") // 抑制 JDK @Nonnull String 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别），方法入参已在 line 29/40/51/57 显式 null 守卫
public final class CountryCodes {

  private CountryCodes() {}

  /** 把任意字符串归一为标准逗号分隔大写码集合（如 "us, ca , gb" → "US,CA,GB"），去重。 */
  public static String normalizeList(String raw) {
    if (raw == null || raw.isBlank()) return "";
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(String::toUpperCase)
        .distinct()
        .collect(Collectors.joining(","));
  }

  /** 把任意字符串按逗号拆为有序 Set<String>，归一化为大写码。可发国家匹配用。 */
  public static Set<String> toSet(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    Set<String> out = new LinkedHashSet<>();
    for (String c : raw.split(",")) {
      String t = c.trim();
      if (!t.isEmpty()) out.add(t.toUpperCase());
    }
    return out;
  }

  /** 把单值国家码（如 "us "）归一为大写（如 "US"）。trim+upper。 */
  public static String normalizeSingle(String raw) {
    if (raw == null) return "";
    return raw.trim().toUpperCase();
  }

  /** 单值是否非空（trim 后） */
  public static boolean isBlank(String raw) {
    return raw == null || raw.isBlank();
  }
}
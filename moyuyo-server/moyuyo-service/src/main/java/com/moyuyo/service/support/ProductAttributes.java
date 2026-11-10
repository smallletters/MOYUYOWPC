package com.moyuyo.service.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 商品 attributes JSON 读取工具。
 * <p>
 * 商品编辑页中所有 WooCommerce 对齐字段（税率、缺货订购、低库存阈值、关联商品等）
 * 都序列化在 mo_product.attributes 这一列 JSON 中，这里统一做容错解析，
 * 避免各业务 Service 重复写 try/catch 并保证默认值一致。
 */
public final class ProductAttributes {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProductAttributes() {
  }

  /** 解析 attributes JSON；为空或格式非法时返回空 Map（业务按默认值处理） */
  public static Map<String, Object> parse(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      Map<String, Object> map = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
      });
      return map == null ? Collections.emptyMap() : map;
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  /** 读取字符串字段，空白视为默认值 */
  public static String str(Map<String, Object> attrs, String key, String defaultValue) {
    Object value = attrs == null ? null : attrs.get(key);
    if (value == null) {
      return defaultValue;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? defaultValue : text;
  }

  /** 读取布尔字段，兼容 true / "true" */
  public static boolean bool(Map<String, Object> attrs, String key, boolean defaultValue) {
    Object value = attrs == null ? null : attrs.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    return "true".equalsIgnoreCase(String.valueOf(value).trim());
  }

  /** 读取整数字段，非法或空返回 null */
  public static Integer intValue(Map<String, Object> attrs, String key) {
    Object value = attrs == null ? null : attrs.get(key);
    if (value == null) {
      return null;
    }
    try {
      String text = String.valueOf(value).trim();
      if (text.isEmpty()) {
        return null;
      }
      return new BigDecimal(text).intValue();
    } catch (Exception e) {
      return null;
    }
  }

  /** 读取数字字段，非法或空返回 null */
  public static BigDecimal decimal(Map<String, Object> attrs, String key) {
    Object value = attrs == null ? null : attrs.get(key);
    if (value == null) {
      return null;
    }
    try {
      String text = String.valueOf(value).trim();
      return text.isEmpty() ? null : new BigDecimal(text);
    } catch (Exception e) {
      return null;
    }
  }

  /** 读取日期（yyyy-MM-dd），非法或空返回 null */
  public static LocalDate date(Map<String, Object> attrs, String key) {
    String text = str(attrs, key, "");
    if (text.isEmpty()) {
      return null;
    }
    try {
      // 兼容带时间段的写法：2026-01-01 00:00:00
      return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
    } catch (Exception e) {
      return null;
    }
  }

  /** 读取 id 数组字段（upsell_ids / cross_sell_ids） */
  public static List<Long> longList(Map<String, Object> attrs, String key) {
    Object value = attrs == null ? null : attrs.get(key);
    if (!(value instanceof List<?> list)) {
      return Collections.emptyList();
    }
    List<Long> result = new ArrayList<>(list.size());
    for (Object item : list) {
      if (item == null) {
        continue;
      }
      try {
        result.add(Long.valueOf(String.valueOf(item).trim()));
      } catch (NumberFormatException ignored) {
        // 忽略非法 id，避免脏数据阻断业务
      }
    }
    return result;
  }

  /**
   * 销售期是否生效：未配置起止日期视为长期生效。
   * 配置后仅当今天处于 [from, to] 区间（含首尾）内才使用促销价。
   */
  public static boolean isSaleWindowActive(Map<String, Object> attrs) {
    LocalDate start = date(attrs, "sale_price_dates_from");
    LocalDate end = date(attrs, "sale_price_dates_to");
    if (start == null && end == null) {
      return true;
    }
    LocalDate today = LocalDate.now();
    return (start == null || !today.isBefore(start)) && (end == null || !today.isAfter(end));
  }

  /**
   * 计算某 SKU 的生效单价（服务端权威价）。
   * <p>
   * 销售期内直接使用当前价（SKU 快照价 / 促销价）；销售期外回退到：
   * 1) 变体自身的 regularPrice（可变商品）
   * 2) 商品级 originalPrice（简单商品）
   * 3) 都缺失时保留当前价
   */
  public static BigDecimal effectivePrice(Map<String, Object> attrs, String skuCode, String spec,
                                          BigDecimal currentPrice, BigDecimal productOriginalPrice) {
    if (isSaleWindowActive(attrs)) {
      return currentPrice;
    }
    BigDecimal regular = variationRegularPrice(attrs, skuCode, spec);
    if (regular == null || regular.compareTo(BigDecimal.ZERO) <= 0) {
      regular = productOriginalPrice;
    }
    if (regular == null || regular.compareTo(BigDecimal.ZERO) <= 0) {
      return currentPrice;
    }
    return regular;
  }

  /** 在 variations 中按 SKU 编码或规格串匹配，取该变体的常规价 */
  private static BigDecimal variationRegularPrice(Map<String, Object> attrs, String skuCode, String spec) {
    Object raw = attrs == null ? null : attrs.get("variations");
    if (!(raw instanceof List<?> list)) {
      return null;
    }
    String code = text(skuCode);
    String specText = text(spec);
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> variation)) {
        continue;
      }
      String variationSku = text(variation.get("sku"));
      String variationSpec = buildVariationSpec(variation.get("attributes"));
      boolean matched = (!variationSku.isEmpty() && variationSku.equals(code))
          || (!variationSpec.isEmpty() && variationSpec.equals(specText));
      if (!matched) {
        continue;
      }
      BigDecimal regular = toDecimal(variation.get("regularPrice"));
      if (regular != null && regular.compareTo(BigDecimal.ZERO) > 0) {
        return regular;
      }
    }
    return null;
  }

  /** 变体规格串，与后端 SKU 落库时的 spec 格式保持一致："颜色:红 / 尺寸:M" */
  private static String buildVariationSpec(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> attribute)) {
        continue;
      }
      String name = text(attribute.get("name"));
      if (name.isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(" / ");
      }
      sb.append(name).append(':').append(text(attribute.get("value")));
    }
    return sb.toString();
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static BigDecimal toDecimal(Object value) {
    if (value == null) {
      return null;
    }
    try {
      String text = String.valueOf(value).trim();
      return text.isEmpty() ? null : new BigDecimal(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

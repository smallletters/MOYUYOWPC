package com.moyuyo.common.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CountryCodes 工具类单元测试。
 * 覆盖入库归一化（normalizeList）、匹配集合（toSet）、单值归一化（normalizeSingle）、空判定（isBlank）四个核心方法。
 */
class CountryCodesTest {

  // ==================== normalizeList ====================

  @Test
  void normalizeList_blankOrNull_returnsEmpty() {
    assertEquals("", CountryCodes.normalizeList(null));
    assertEquals("", CountryCodes.normalizeList(""));
    assertEquals("", CountryCodes.normalizeList("   "));
    assertEquals("", CountryCodes.normalizeList(", , ,"));
  }

  @Test
  void normalizeList_normalizeTrimUpperDedupe() {
    assertEquals("US,CA,GB", CountryCodes.normalizeList("us, CA ,us , ca , GB"));
  }

  @Test
  void normalizeList_preserveInsertionOrder() {
    // 业务上 = 国白名单的内部顺序很重要（与 DB 中 country_codes 字符串对齐），故用 LinkedHashSet
    assertEquals("GB,US,CA", CountryCodes.normalizeList("GB,US,CA,US"));
  }

  // ==================== toSet ====================

  @Test
  void toSet_blank_returnsEmpty() {
    assertTrue(CountryCodes.toSet(null).isEmpty());
    assertTrue(CountryCodes.toSet("").isEmpty());
  }

  @Test
  void toSet_dedupeAndUpper() {
    Set<String> set = CountryCodes.toSet(" us , CA ,US ");
    assertEquals(2, set.size());
    assertTrue(set.contains("US"));
    assertTrue(set.contains("CA"));
  }

  // ==================== normalizeSingle ====================

  @Test
  void normalizeSingle_trimAndUpper() {
    assertEquals("US", CountryCodes.normalizeSingle(" us "));
    assertEquals("CA", CountryCodes.normalizeSingle("ca"));
    assertEquals("", CountryCodes.normalizeSingle(""));
    assertEquals("", CountryCodes.normalizeSingle(null));
  }

  // ==================== isBlank ====================

  @Test
  void isBlank_recognizesWhitespace() {
    assertTrue(CountryCodes.isBlank(null));
    assertTrue(CountryCodes.isBlank(""));
    assertTrue(CountryCodes.isBlank("   "));
    assertFalse(CountryCodes.isBlank("US"));
    assertFalse(CountryCodes.isBlank(" us "));  // 视为非空（含非空白字符）
  }
}
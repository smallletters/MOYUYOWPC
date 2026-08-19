package com.moyuyo.service.impl;

import com.moyuyo.dao.admin.entity.SensitiveWordEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveWordFilterTest {

  @Test
  void shouldDetectEnabledExactWord() {
    SensitiveWordEntity word = new SensitiveWordEntity();
    word.setWord("违禁品");
    word.setMatchMode("EXACT");
    word.setStatus("ENABLED");

    SensitiveWordFilter filter = new SensitiveWordFilter(List.of(word));

    assertTrue(filter.contains("这是违禁品内容"));
  }

  @Test
  void shouldIgnoreDisabledWord() {
    SensitiveWordEntity word = new SensitiveWordEntity();
    word.setWord("违禁品");
    word.setMatchMode("EXACT");
    word.setStatus("DISABLED");

    SensitiveWordFilter filter = new SensitiveWordFilter(List.of(word));

    assertFalse(filter.contains("这是违禁品内容"));
  }

  @Test
  void shouldDetectRegexWord() {
    SensitiveWordEntity word = new SensitiveWordEntity();
    word.setWord("\\b假\\S*货\\b");
    word.setMatchMode("REGEX");
    word.setStatus("ENABLED");

    SensitiveWordFilter filter = new SensitiveWordFilter(List.of(word));

    assertTrue(filter.contains("这里是假货商品"));
  }
}

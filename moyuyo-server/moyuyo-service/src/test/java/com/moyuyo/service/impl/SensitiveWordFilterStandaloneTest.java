package com.moyuyo.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveWordFilterStandaloneTest {

  static class Word {
    String text;
    String mode;
    String status;
  }

  static class TestableFilter {
    private final List<Word> words;

    TestableFilter(List<Word> words) {
      this.words = words;
    }

    boolean contains(String text) {
      if (text == null || text.isEmpty()) {
        return false;
      }
      return words.stream()
          .filter(w -> "ENABLED".equalsIgnoreCase(w.status) && w.text != null && !w.text.isEmpty())
          .anyMatch(w -> {
            if ("REGEX".equalsIgnoreCase(w.mode)) {
              return java.util.regex.Pattern.compile(w.text).matcher(text).find();
            }
            return text.contains(w.text);
          });
    }
  }

  @Test
  void shouldDetectEnabledExactWord() {
    Word w = new Word();
    w.text = "违禁品";
    w.mode = "EXACT";
    w.status = "ENABLED";
    TestableFilter filter = new TestableFilter(List.of(w));
    assertTrue(filter.contains("这是违禁品内容"));
  }

  @Test
  void shouldIgnoreDisabledWord() {
    Word w = new Word();
    w.text = "违禁品";
    w.mode = "EXACT";
    w.status = "DISABLED";
    TestableFilter filter = new TestableFilter(List.of(w));
    assertFalse(filter.contains("这是违禁品内容"));
  }

  @Test
  void shouldDetectRegexWord() {
    Word w = new Word();
    w.text = "\\b假\\S*货\\b";
    w.mode = "REGEX";
    w.status = "ENABLED";
    TestableFilter filter = new TestableFilter(List.of(w));
    assertTrue(filter.contains("这里是假货商品"));
  }
}
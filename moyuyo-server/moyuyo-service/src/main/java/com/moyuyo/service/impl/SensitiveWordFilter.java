package com.moyuyo.service.impl;

import com.moyuyo.dao.admin.entity.SensitiveWordEntity;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感词运行时过滤器。
 */
public class SensitiveWordFilter {

  private final List<SensitiveWordEntity> words;

  public SensitiveWordFilter(List<SensitiveWordEntity> words) {
    this.words = words;
  }

  /**
   * 判断文本是否命中启用的敏感词。
   */
  public boolean contains(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    return words.stream()
        .filter(this::isEnabled)
        .anyMatch(word -> matches(word, text));
  }

  private boolean isEnabled(SensitiveWordEntity word) {
    return word != null && "ENABLED".equalsIgnoreCase(word.getStatus())
        && word.getWord() != null && !word.getWord().isEmpty();
  }

  private boolean matches(SensitiveWordEntity word, String text) {
    String mode = word.getMatchMode();
    if ("REGEX".equalsIgnoreCase(mode)) {
      return Pattern.compile(word.getWord()).matcher(text).find();
    }
    return text.contains(word.getWord());
  }
}

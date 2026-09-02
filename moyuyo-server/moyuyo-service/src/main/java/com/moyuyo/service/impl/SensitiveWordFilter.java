package com.moyuyo.service.impl;

import com.moyuyo.dao.admin.entity.SensitiveWordEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

  /**
   * 找出文本中所有命中的敏感词(去重 + 保序)。
   * 用于前端实时提示:用户输入到包含敏感词的文字时,提示"该词包含敏感词"。
   * 注意:REGEX 模式下命中的词会用 raw pattern 字符串,不展开匹配到的子串。
   */
  public List<String> findHits(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }
    Set<String> seen = new LinkedHashSet<>();
    for (SensitiveWordEntity w : words) {
      if (!isEnabled(w)) continue;
      if (matches(w, text)) {
        seen.add(w.getWord());
      }
    }
    return new ArrayList<>(seen);
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

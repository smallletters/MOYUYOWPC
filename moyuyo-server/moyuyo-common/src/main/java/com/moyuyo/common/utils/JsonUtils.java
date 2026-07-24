package com.moyuyo.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;

/**
 * JSON 工具类 — 统一序列化/反序列化
 */
public class JsonUtils {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String toJsonArray(List<String> items) {
    if (items == null || items.isEmpty()) {
      return "[]";
    }
    try {
      return MAPPER.writeValueAsString(items);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("JSON序列化失败", e);
    }
  }

  public static List<String> parseStringArray(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException("JSON反序列化失败", e);
    }
  }
}

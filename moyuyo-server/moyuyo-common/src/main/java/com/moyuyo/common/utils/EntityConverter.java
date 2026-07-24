package com.moyuyo.common.utils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体与 Map 转换工具类 — 消除 Entity-to-Map 重复手动转换代码
 */
public class EntityConverter {

  /**
   * 将单个实体转换为 Map，使用反射提取所有字段
   */
  public static Map<String, Object> toMap(Object entity) {
    if (entity == null) return Collections.emptyMap();
    Map<String, Object> map = new LinkedHashMap<>();
    for (Field field : getAllFields(entity.getClass())) {
      try {
        field.setAccessible(true);
        Object value = field.get(entity);
        // 跳过 static/transient 字段
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
            || java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
          continue;
        }
        map.put(field.getName(), value);
      } catch (IllegalAccessException ignored) {
        // 跳过无法访问的字段
      }
    }
    return map;
  }

  /**
   * 将实体列表批量转换为 Map 列表
   */
  public static <T> List<Map<String, Object>> toMapList(List<T> entities) {
    if (entities == null || entities.isEmpty()) return Collections.emptyList();
    return entities.stream().map(EntityConverter::toMap).collect(Collectors.toList());
  }

  /**
   * 获取类及其父类的所有字段（跳过 Object 类）
   */
  private static List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
      Collections.addAll(fields, current.getDeclaredFields());
      current = current.getSuperclass();
    }
    return fields;
  }
}

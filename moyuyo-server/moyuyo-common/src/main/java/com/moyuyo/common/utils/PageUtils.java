package com.moyuyo.common.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类 — 统一 Entity → VO 转换
 */
public class PageUtils {

  public static <E, V> IPage<V> convertPage(IPage<E> entityPage, Function<E, V> converter) {
    IPage<V> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    List<V> voList = entityPage.getRecords().stream().map(converter).collect(Collectors.toList());
    voPage.setRecords(voList);
    return voPage;
  }
}

package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.AdminBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 黑名单服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminBlacklistServiceImpl implements AdminBlacklistService {

  @Override
  public Map<String, Object> listAll(String type, int page, int size) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", new ArrayList<>());
    result.put("total", 0L);
    result.put("page", page);
    result.put("size", size);
    return result;
  }

  @Override
  public void create(Map<String, Object> data) {
    // 添加黑名单逻辑
  }

  @Override
  public void batchCreate(List<Map<String, Object>> list) {
    // 批量添加黑名单逻辑
  }

  @Override
  public void delete(Long id) {
    // 移除黑名单逻辑
  }

  @Override
  public void update(Long id, Map<String, Object> data) {
    // 更新黑名单逻辑
  }
}

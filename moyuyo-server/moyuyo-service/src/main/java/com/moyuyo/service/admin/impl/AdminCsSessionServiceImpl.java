package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.AdminCsSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 客服会话服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminCsSessionServiceImpl implements AdminCsSessionService {

  @Override
  public Map<String, Object> listAll(int page, int size, String status) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("list", new ArrayList<>());
    result.put("total", 0L);
    result.put("page", page);
    result.put("size", size);
    return result;
  }

  @Override
  public Map<String, Object> getById(Long id) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    return result;
  }

  @Override
  public Map<String, Object> getStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalSessions", 0);
    stats.put("pendingSessions", 0);
    stats.put("activeSessions", 0);
    stats.put("closedSessions", 0);
    return stats;
  }
}

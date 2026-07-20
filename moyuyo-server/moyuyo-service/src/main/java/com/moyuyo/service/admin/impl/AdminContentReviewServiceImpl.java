package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.AdminContentReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 内容审核服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminContentReviewServiceImpl implements AdminContentReviewService {

  @Override
  public Map<String, Object> listAll(int page, int size, String contentType, String status) {
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
    result.put("status", "待审核");
    return result;
  }

  @Override
  public void approve(Long id, Long reviewerId) {
    // 审核通过逻辑
  }

  @Override
  public void reject(Long id, Long reviewerId, String reason, String comment) {
    // 审核驳回逻辑
  }
}

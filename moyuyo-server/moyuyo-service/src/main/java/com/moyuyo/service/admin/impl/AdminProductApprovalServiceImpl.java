package com.moyuyo.service.admin.impl;

import com.moyuyo.dao.mapper.ProductMapper;
import com.moyuyo.service.admin.AdminProductApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 商品审核服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminProductApprovalServiceImpl implements AdminProductApprovalService {

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
    result.put("status", "待审核");
    result.put("message", "商品审核信息");
    return result;
  }

  @Override
  public void approve(Long id, Long reviewerId) {
    // 审核通过逻辑
  }

  @Override
  public void reject(Long id, Long reviewerId, String reason) {
    // 审核驳回逻辑
  }

  @Override
  public void setUrgent(Long id) {
    // 标记加急逻辑
  }
}

package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.AdminTariffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 关税服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminTariffServiceImpl implements AdminTariffService {

  @Override
  public List<Map<String, Object>> listConfigs(String countryCode) {
    return new ArrayList<>();
  }

  @Override
  public void createConfig(Map<String, Object> data) {
    // 创建关税配置逻辑
  }

  @Override
  public void updateConfig(Map<String, Object> data) {
    // 更新关税配置逻辑
  }

  @Override
  public void deleteConfig(Long id) {
    // 删除关税配置逻辑
  }

  @Override
  public Map<String, Object> calculate(String countryCode, BigDecimal amount, String category) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("countryCode", countryCode);
    result.put("amount", amount);
    result.put("category", category);
    result.put("tariff", BigDecimal.ZERO);
    result.put("total", amount);
    return result;
  }
}

package com.moyuyo.service.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 管理后台关税服务
 */
public interface AdminTariffService {

  /**
   * 关税配置列表
   */
  List<Map<String, Object>> listConfigs(String countryCode);

  /**
   * 创建关税配置
   */
  void createConfig(Map<String, Object> data);

  /**
   * 更新关税配置
   */
  void updateConfig(Map<String, Object> data);

  /**
   * 删除关税配置
   */
  void deleteConfig(Long id);

  /**
   * 计算关税
   */
  Map<String, Object> calculate(String countryCode, BigDecimal amount, String category);
}

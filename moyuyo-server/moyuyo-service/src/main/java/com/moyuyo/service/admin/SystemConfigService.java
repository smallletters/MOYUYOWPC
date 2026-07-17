package com.moyuyo.service.admin;

import java.util.Map;

/**
 * 系统配置服务
 */
public interface SystemConfigService {

  /**
   * 获取配置
   */
  Map<String, Object> getConfig(String group);

  /**
   * 保存配置
   */
  void saveConfig(String group, Map<String, Object> config);
}

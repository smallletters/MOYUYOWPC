package com.moyuyo.service.admin.impl;

import com.moyuyo.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统配置服务实现
 */
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

  @Override
  public Map<String, Object> getConfig(String group) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("group", group);
    config.put("siteName", "MOYUYO 宠物商城");
    config.put("siteDescription", "高端宠物用品商城");
    config.put("recordNo", "粤ICP备xxxxxxxx号");
    config.put("contactEmail", "admin@moyuyo.com");
    return config;
  }

  @Override
  public void saveConfig(String group, Map<String, Object> config) {
    // 待实现：保存系统配置
  }
}

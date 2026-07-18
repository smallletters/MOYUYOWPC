package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.admin.entity.SystemConfigEntity;
import com.moyuyo.dao.admin.mapper.SystemConfigMapper;
import com.moyuyo.service.admin.SystemConfigService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现
 */
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

  private final SystemConfigMapper systemConfigMapper;

  public SystemConfigServiceImpl(SystemConfigMapper systemConfigMapper) {
    this.systemConfigMapper = systemConfigMapper;
  }

  @Override
  public Map<String, Object> getConfig(String group) {
    // 从 mo_system_config 表查询所有配置，按 config_key 放入 Map
    List<SystemConfigEntity> configList = systemConfigMapper.selectList(null);
    Map<String, Object> config = new LinkedHashMap<>();
    for (SystemConfigEntity entity : configList) {
      config.put(entity.getConfigKey(), entity.getConfigValue());
    }
    return config;
  }

  @Override
  public void saveConfig(String group, Map<String, Object> config) {
    // 遍历参数 map，使用 insert/update 保存到 mo_system_config 表
    for (Map.Entry<String, Object> entry : config.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue() != null ? entry.getValue().toString() : "";

      // 检查是否已存在
      SystemConfigEntity existing = systemConfigMapper.selectOne(
          new LambdaQueryWrapper<SystemConfigEntity>()
              .eq(SystemConfigEntity::getConfigKey, key));

      if (existing != null) {
        // 已存在则更新
        existing.setConfigValue(value);
        systemConfigMapper.updateById(existing);
      } else {
        // 不存在则插入
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        systemConfigMapper.insert(entity);
      }
    }
  }

  @Override
  public void saveConfig(Map<String, String> config) {
    // 遍历参数 map，使用 insert/update 保存到 mo_system_config 表
    for (Map.Entry<String, String> entry : config.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue() != null ? entry.getValue() : "";

      SystemConfigEntity existing = systemConfigMapper.selectOne(
          new LambdaQueryWrapper<SystemConfigEntity>()
              .eq(SystemConfigEntity::getConfigKey, key));

      if (existing != null) {
        existing.setConfigValue(value);
        systemConfigMapper.updateById(existing);
      } else {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        systemConfigMapper.insert(entity);
      }
    }
  }
}

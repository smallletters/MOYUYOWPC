package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.AppVersionEntity;
import com.moyuyo.dao.admin.mapper.AppVersionMapper;
import com.moyuyo.service.admin.AdminAppVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台应用版本服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminAppVersionServiceImpl implements AdminAppVersionService {

  private final AppVersionMapper appVersionMapper;

  @Override
  public Page<Map<String, Object>> listAll(String appType, int page, int size) {
    LambdaQueryWrapper<AppVersionEntity> wrapper = new LambdaQueryWrapper<>();
    if (appType != null && !appType.isEmpty()) {
      wrapper.eq(AppVersionEntity::getAppType, appType);
    }
    wrapper.orderByDesc(AppVersionEntity::getCreateTime);

    Page<AppVersionEntity> entityPage = appVersionMapper.selectPage(new Page<>(page, size), wrapper);
    Page<Map<String, Object>> resultPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    resultPage.setRecords(entityPage.getRecords().stream().map(e -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", e.getId());
      item.put("appType", e.getAppType());
      item.put("versionCode", e.getVersionCode());
      item.put("versionName", e.getVersionName());
      item.put("updateTitle", e.getUpdateTitle());
      item.put("updateDesc", e.getUpdateDesc());
      item.put("downloadUrl", e.getDownloadUrl());
      item.put("forceUpdate", e.getForceUpdate());
      item.put("fileSize", e.getFileSize());
      item.put("status", e.getStatus());
      item.put("publishTime", e.getPublishTime());
      item.put("createTime", e.getCreateTime());
      return item;
    }).collect(Collectors.toList()));
    return resultPage;
  }

  @Override
  public void create(AppVersionEntity entity) {
    entity.setStatus("DRAFT");
    appVersionMapper.insert(entity);
  }

  @Override
  public void update(AppVersionEntity entity) {
    appVersionMapper.updateById(entity);
  }

  @Override
  public void publish(Long id) {
    AppVersionEntity entity = appVersionMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("PUBLISHED");
      entity.setPublishTime(LocalDateTime.now());
      appVersionMapper.updateById(entity);
    }
  }

  @Override
  public void rollback(Long id) {
    AppVersionEntity entity = appVersionMapper.selectById(id);
    if (entity != null) {
      entity.setStatus("ROLLED_BACK");
      appVersionMapper.updateById(entity);
    }
  }

  @Override
  public void delete(Long id) {
    appVersionMapper.deleteById(id);
  }
}

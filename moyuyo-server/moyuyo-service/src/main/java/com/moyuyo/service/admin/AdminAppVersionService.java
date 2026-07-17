package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.AppVersionEntity;

import java.util.Map;

/**
 * 管理后台应用版本服务
 */
public interface AdminAppVersionService {

  /**
   * 版本列表（分页）
   */
  Page<Map<String, Object>> listAll(String appType, int page, int size);

  /**
   * 创建版本
   */
  void create(AppVersionEntity entity);

  /**
   * 更新版本
   */
  void update(AppVersionEntity entity);

  /**
   * 发布版本
   */
  void publish(Long id);

  /**
   * 回滚版本
   */
  void rollback(Long id);

  /**
   * 删除版本
   */
  void delete(Long id);
}

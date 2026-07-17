package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.admin.entity.PriceStrategyEntity;

import java.util.Map;

/**
 * 管理后台价格策略服务
 */
public interface AdminPriceStrategyService {

  /**
   * 价格策略列表（分页、筛选）
   */
  Page<Map<String, Object>> listAll(String type, Boolean enabled, int page, int size);

  /**
   * 创建策略
   */
  void create(PriceStrategyEntity entity);

  /**
   * 更新策略
   */
  void update(PriceStrategyEntity entity);

  /**
   * 删除策略
   */
  void delete(Long id);

  /**
   * 启用/禁用策略
   */
  void toggle(Long id, Boolean enabled);
}

package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.LogisticsEntity;

import java.util.Map;

/**
 * 管理后台 - 物流管理服务接口
 */
public interface AdminLogisticsService {

  /**
   * 分页查询物流列表
   */
  Page<LogisticsEntity> listAll(String status, String carrier, int page, int size);

  /**
   * 物流统计
   */
  Map<String, Object> stats();

  /**
   * 更新物流承运商和运单号
   */
  void updateCarrier(Long id, String carrier, String trackingNo);
}

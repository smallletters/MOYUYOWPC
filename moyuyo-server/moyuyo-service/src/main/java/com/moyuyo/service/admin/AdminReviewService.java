package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.ProductReviewEntity;

import java.util.Map;

/**
 * 管理后台 - 评价管理服务接口
 */
public interface AdminReviewService {

  /**
   * 分页查询评价列表
   */
  Page<ProductReviewEntity> listAll(String status, int page, int size);

  /**
   * 评价统计（总数、好评率等）
   */
  Map<String, Object> stats();

  /**
   * 回复评价（更新评价的回复内容）
   */
  void reply(Long id, String content);

  /**
   * 逻辑删除评价
   */
  void delete(Long id);

  /**
   * 审核通过
   */
  void approve(Long id);

  /**
   * 审核驳回
   */
  void reject(Long id);
}

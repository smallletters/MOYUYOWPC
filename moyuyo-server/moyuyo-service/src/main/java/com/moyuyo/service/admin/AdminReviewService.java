package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.ProductReviewEntity;

import java.util.List;
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
   * 今日评价审核统计：今日 PENDING / APPROVED / REJECTED 数量 + 通过率 + 昨日对比值
   */
  Map<String, Object> todayStats();

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

  /**
   * 批量审核通过：单次 SQL 更新所有目标评价状态，仅处理状态为 PENDING 的评价（幂等）。
   * 同步对涉及到的订单尝试推进完结（与单条 approve 行为对齐）。
   *
   * @return 实际被审核通过的评价数量
   */
  int batchApprove(List<Long> ids);
}

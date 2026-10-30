package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyuyo.dao.entity.FeedbackEntity;

import java.util.Map;

/**
 * 管理后台 - 意见反馈服务。
 *
 * <p>与 {@link AdminComplaintService} 共用 mo_feedback 表：
 * 投诉是反馈的一个子类型（type=COMPLAINT），此前后台只在"投诉管理"里处理，
 * 用户提交的建议/缺陷（SUGGESTION/BUG/OTHER）没有任何查看入口。
 */
public interface AdminFeedbackService {

  /**
   * 反馈列表（分页）。
   *
   * @param status  状态筛选（PENDING/PROCESSING/RESOLVED/CLOSED），空则不筛
   * @param type    类型筛选（BUG/SUGGESTION/COMPLAINT/OTHER），空则不筛
   * @param keyword 关键词，匹配反馈内容与联系方式，空则不筛
   */
  IPage<FeedbackEntity> listFeedback(String status, String type, String keyword, int page, int size);

  /** 反馈详情，不存在时抛 IllegalArgumentException */
  FeedbackEntity getDetail(Long id);

  /**
   * 处理反馈：更新状态，可选写入回复内容（同时记录回复时间）。
   *
   * @param status       目标状态，空则保持不变
   * @param replyContent 回复内容，空则不覆盖原回复
   */
  void handle(Long id, String status, String replyContent);

  /** 反馈统计：总数 / 待处理 / 处理中 / 已完成 */
  Map<String, Object> stats();
}

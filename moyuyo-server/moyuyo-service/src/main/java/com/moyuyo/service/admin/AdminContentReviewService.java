package com.moyuyo.service.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理后台内容审核服务
 */
public interface AdminContentReviewService {

  /**
   * 内容审核列表（分页）
   *
   * @param page 页码
   * @param size 每页大小
   * @param contentType 内容种类筛选(POST/COMMENT等)
   * @param status 审核状态筛选
   * @param reasonLike 违规类型关键字(reason LIKE '%xxx%'),对应前端 tab 筛选
   */
  Map<String, Object> listAll(int page, int size, String contentType, String status, String reasonLike);

  /**
   * 审核详情
   */
  Map<String, Object> getById(Long id);

  /**
   * 审核通过
   */
  void approve(Long id, Long reviewerId);

  /**
   * 审核驳回
   */
  void reject(Long id, Long reviewerId, String reason, String comment);

  /**
   * 隐藏内容
   */
  void hide(Long id);

  /**
   * 删除内容
   */
  void deleteContent(Long id);

  /**
   * 封禁内容
   * @param id 审核记录ID
   * @param reviewerId 操作人(管理员ID)
   * @param banType 违规类型(色情/暴力/仇恨言论/侵权/虚假信息/虐待动物)
   * @param comment 备注(可选)
   */
  void ban(Long id, Long reviewerId, String banType, String comment);

  /**
   * 审核统计数据
   */
  Map<String, Object> getStats();

  /**
   * 审核趋势数据
   */
  List<Map<String, Object>> getTrend(int days);

  /**
   * 灌入测试数据(仅 dev):插入 6 条 reason 为各种违规类型的审核记录,
   * 便于演示 tab 筛选/弹窗功能。生产环境应禁用。
   * @return 实际插入条数
   */
  int seedTestData();
}

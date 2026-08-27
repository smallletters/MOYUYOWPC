package com.moyuyo.common.dto.admin.userprofile;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户访问过的页面（来自 mo_visit_log，按 page_url 聚合）
 */
@Data
public class UserVisitedPageResponse {

  /** 访问日志主键 */
  private Long id;

  /** 页面 URL（来源 referrer / pageUrl） */
  private String pageUrl;

  /** 页面名称（来源 pageName） */
  private String pageName;

  /** 该页面被该用户访问的次数 */
  private Integer visitCount;

  /** 该用户在该页面的累计停留时长（秒） */
  private Integer stayDuration;

  /** 最近访问时间 */
  private LocalDateTime lastVisitTime;
}
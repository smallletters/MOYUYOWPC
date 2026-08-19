package com.moyuyo.common.dto.admin.ticket;

import lombok.Data;

import java.util.List;

@Data
public class TicketDetailResponse {

  private Long id;
  private String ticketNo;
  private String title;
  private String content;
  /** PENDING / PROCESSING / CLOSED / RESOLVED */
  private String status;
  private String assignee;
  /** 首响耗时（分钟） */
  private Integer firstResponseMinutes;
  /** 回复内容（合并展示） */
  private String replyContent;
  /** 完整回复历史（按时间升序） */
  private List<String> replies;
  private String createdAt;
}
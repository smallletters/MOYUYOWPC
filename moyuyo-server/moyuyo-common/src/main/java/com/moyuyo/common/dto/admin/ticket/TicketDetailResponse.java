package com.moyuyo.common.dto.admin.ticket;

import lombok.Data;

import java.util.List;

@Data
public class TicketDetailResponse {

  private Long id;
  private String title;
  private String content;
  private String status;
  private String assignee;
  private List<String> replies;
  private String createdAt;
}

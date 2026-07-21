package com.moyuyo.common.dto.admin.ticket;

import lombok.Data;

@Data
public class TicketResponse {

  private Long id;
  private String title;
  private String type;
  private String status;
  private String priority;
  private String assignee;
  private String createdAt;
}

package com.moyuyo.common.dto.admin.ticket;

import lombok.Data;

@Data
public class TicketStatsResponse {

  private int total;
  private int open;
  private int processing;
  private int resolved;
  private int closed;
}

package com.moyuyo.common.dto.admin.ticket;

import lombok.Data;

@Data
public class TicketStatusRequest {

  private String status;
  private String remark;
}

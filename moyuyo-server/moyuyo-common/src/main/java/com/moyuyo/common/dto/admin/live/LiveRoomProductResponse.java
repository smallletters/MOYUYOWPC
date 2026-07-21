package com.moyuyo.common.dto.admin.live;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LiveRoomProductResponse {

  private Long id;
  private String name;
  private BigDecimal price;
  private String image;
}

package com.moyuyo.common.dto.admin.live;

import lombok.Data;

import java.util.List;

@Data
public class LiveRoomResponse {

  private Long id;
  private String name;
  private String status;
  private String startTime;
  private int viewerCount;
  private int productCount;
  private List<LiveRoomProductResponse> products;
}

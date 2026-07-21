package com.moyuyo.common.dto.admin.userprofile;

import lombok.Data;

@Data
public class UserBehaviorResponse {

  private String behaviorType;
  private Integer count;
  private String lastTime;
}

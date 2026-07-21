package com.moyuyo.common.dto.admin.userprofile;

import lombok.Data;

@Data
public class UserProfileResponse {

  private Long userId;
  private String nickname;
  private String avatar;
  private String email;
  private String phone;
  private Integer orderCount;
  private String registerTime;
  private Integer totalSpent;
}

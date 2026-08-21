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
  // 性别（MALE/FEMALE/OTHER/UNDISCLOSED），null 时表示未填写
  private String gender;
  // 年龄（基于 birthday 计算得到的周岁），null 时表示未填写
  private Integer age;
}

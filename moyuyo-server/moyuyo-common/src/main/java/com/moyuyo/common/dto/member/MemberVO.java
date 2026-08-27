package com.moyuyo.common.dto.member;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MemberVO {

  private Long userId;

  private String level;

  private Integer growthValue;

  private Integer points;

  private BigDecimal balance;

  /** 会员卡号（基于 userId 确定性生成，对用户稳定） */
  private String memberNo;
}

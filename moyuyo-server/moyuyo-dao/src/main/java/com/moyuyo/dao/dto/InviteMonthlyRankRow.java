package com.moyuyo.dao.dto;

import lombok.Data;

/**
 * 月度邀请排行查询行：某个邀请人在统计区间内成功邀请的人数。
 */
@Data
public class InviteMonthlyRankRow {

  private Long userId;

  private Long inviteCount;
}

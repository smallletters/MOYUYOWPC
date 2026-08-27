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
  /** 累计消费（分），前端按需除以 100 展示 */
  private Integer totalSpent;
  // 性别（MALE/FEMALE/OTHER/UNDISCLOSED），null 时表示未填写
  private String gender;
  // 年龄（基于 birthday 计算得到的周岁），null 时表示未填写
  private Integer age;

  // ===== 用户画像新增字段（实际业务数据） =====

  /** 用户积分余额（来自 mo_user.points） */
  private Integer points;

  /** 用户状态：1 启用 / 0 禁用 */
  private Integer status;

  /** 会员等级 NORMAL/SILVER/GOLD/PLATINUM/DIAMOND；未入会时为 NORMAL */
  private String memberLevel;

  /** 成长值（mo_member.growth_value） */
  private Integer growthValue;

  /** 会员卡号：基于 userId 确定性生成 */
  private String memberNo;

  /** 最近登录时间（yyyy-MM-dd HH:mm:ss） */
  private String lastLoginTime;
}
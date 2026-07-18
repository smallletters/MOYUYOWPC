package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户行为实体
 */
@Data
@TableName("mo_user_behavior")
public class UserBehaviorEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 用户ID */
  private Long userId;

  /** 行为类型：VIEW_PRODUCT/SEARCH/ADD_CART/PLACE_ORDER/FAVORITE */
  private String behaviorType;

  /** 次数 */
  private Integer count;

  /** 最后发生时间 */
  private LocalDateTime lastTime;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

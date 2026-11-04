package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销活动用户用量（用于按用户原子限购）
 */
@Data
@TableName("mo_campaign_user_usage")
public class CampaignUserUsageEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long campaignId;

  private Long userId;

  private Integer usedCount;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}

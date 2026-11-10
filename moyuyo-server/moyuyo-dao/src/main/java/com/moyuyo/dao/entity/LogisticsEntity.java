package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_logistics")
public class LogisticsEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long orderId;

  private String carrier;

  private String trackingNumber;

  private String traces;

  /** 轨迹最后更新时间（用于"物流停滞"监控规则精确判断，人工录入轨迹时刷新） */
  private LocalDateTime lastTrackTime;

  private LocalDateTime shippedAt;

  private LocalDateTime receivedAt;
}

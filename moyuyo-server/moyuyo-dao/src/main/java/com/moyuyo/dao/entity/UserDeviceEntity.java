package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_device")
public class UserDeviceEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String deviceId;

  /** IOS / ANDROID / WEB / MAC / WINDOWS — 对应表字段 platform */
  private String platform;

  /** 设备型号(iphone15/xiaomi-14 等) — 对应表字段 model */
  private String model;

  private String osVersion;

  private String appVersion;

  /** 推送 token(APP 端) */
  private String pushToken;

  /** 是否 2FA 可信设备(0/1) */
  private Integer trusted;

  private LocalDateTime lastActive;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
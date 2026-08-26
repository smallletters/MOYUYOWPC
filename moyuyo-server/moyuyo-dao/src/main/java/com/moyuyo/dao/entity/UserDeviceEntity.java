package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mo_user_device")
public class UserDeviceEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String deviceId;

  private String deviceName;

  /** IOS / ANDROID / WEB / MAC / WINDOWS */
  private String deviceType;

  private String osVersion;

  private String appVersion;

  private String ipAddress;

  private String location;

  private Integer trusted;

  private LocalDateTime lastActive;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime loginTime;
}
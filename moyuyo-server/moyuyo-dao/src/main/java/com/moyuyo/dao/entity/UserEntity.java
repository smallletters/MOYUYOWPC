package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mo_user")
public class UserEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String email;

    private String passwordHash;

    private String phone;

    private String nickname;

    private String avatar;

    private LocalDate birthday;

    private String country;

    private String locale;

    private String timezone;

    private Integer points;

    private Boolean twoFactorEnabled;

    private Boolean emailVerified;

    private Boolean marketingOptIn;

    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime deleteScheduledAt;

    private String oauthProvider;

    private String oauthUid;

    @TableField(exist = false)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

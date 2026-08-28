package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 手机短信验证码实体。
 * 用于手机号登录、注册、重置密码等场景。
 */
@Data
@TableName("mo_sms_code")
public class SmsCodeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 手机号（带国家区号，如 +8613800000000） */
    private String phone;

    /** 6 位验证码 */
    private String code;

    /** 用途：LOGIN / REGISTER / RESET_PASSWORD */
    private String purpose;

    /** 是否已使用 0=否 1=是 */
    private Integer used;

    /** 验证失败次数（达 5 自动失效） */
    private Integer failCount;

    /** 过期时间 */
    private LocalDateTime expireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
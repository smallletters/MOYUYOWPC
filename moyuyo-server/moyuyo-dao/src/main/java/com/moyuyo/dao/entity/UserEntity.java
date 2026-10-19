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

    // 用户性别（MALE/FEMALE/OTHER/UNDISCLOSED），由 V20260821_01 迁移新增字段
    private String gender;

    // 用户简介：个人主页展示文案，V20260924_01 迁移新增字段
    private String bio;

    private String country;

    // 注册渠道（web/app/wechat），由 V20260819_02 迁移新增字段
    private String registrationChannel;

    private String locale;

    private String timezone;

    private Integer points;

    // 因"未支付订单"被冻结(占用)的积分：下单即冻结，支付成功转实扣，取消/超时解冻回退。
    // 可用积分 = points - pointsFrozen（MemberService.getPointsBalance 口径）。
    // 由 V20260929_01__add_user_points_frozen.sql 迁移新增，DB 端 DEFAULT 0，此处字段缺省值仅为 Java 兜底。
    //
    // updateStrategy = NEVER：本列只允许由 MemberService 的
    // freezePoints / unfreezePoints / consumeFrozenPoints 通过条件 UPDATE
    // （LambdaUpdateWrapper#setSql，不受本策略影响）原子改写；禁止任何 updateById(整实体) 回写本列。
    // 原因：全项目大量路径是"读出整实体 → 改若干字段 → userMapper.updateById(user)"
    // （签到/中奖/任务奖励/评价/邀请/后台调分等），若这些路径也把本列写回，
    // 就会覆盖并发中刚变更的 points_frozen，导致"冻结被静默释放"的丢更新：
    // 同一批积分被多笔待付款订单重复占用、支付/取消时条件更新落空走人工对账。
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer pointsFrozen;

    private Boolean twoFactorEnabled;

    private Boolean emailVerified;

    private Boolean marketingOptIn;

    // 隐私开关 4 项（由 V20260916_01 迁移新增）：
    // 默认值在 DB 端通过 DEFAULT 1/0 控制,Entity 字段缺省值仅为 Java 兜底
    private Boolean publicFavorites;

    private Boolean allowViewProfile;

    private Boolean showOnlineStatus;

    private Boolean allowMessages;

    /** 最后一次数据导出请求时间,用于限流（V20260916_01 新增） */
    private LocalDateTime dataExportRequestedAt;

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

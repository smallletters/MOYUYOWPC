package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用版本管理实体
 */
@Data
@TableName("mo_app_version")
public class AppVersionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** IOS/ANDROID */
    private String appType;

    /** 版本号（数字） */
    private Integer versionCode;

    /** 版本号（显示） */
    private String versionName;

    /** 更新标题 */
    private String updateTitle;

    /** 更新说明 */
    private String updateDesc;

    /** 下载链接 */
    private String downloadUrl;

    /** 是否强制更新 */
    private Boolean forceUpdate;

    /** 文件大小 */
    private String fileSize;

    /** DRAFT/PUBLISHED/ROLLED_BACK */
    private String status;

    /** 发布时间 */
    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

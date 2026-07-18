package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据导出请求实体（对应 mo_data_export_request 表）
 */
@Data
@TableName("mo_data_export_request")
public class DataExportRequestEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 导出标识 */
    private String exportId;

    /** 状态：PENDING/PROCESSING/COMPLETED/FAILED */
    private String status;

    /** 下载链接 */
    private String downloadUrl;

    /** 请求类型/导入类型 */
    private String requestType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 完成时间 */
    private LocalDateTime completeTime;
}

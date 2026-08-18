package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体（与 mo_operation_log 表一一对应）
 * <p>
 * 用于持久化 @OperationLog 标注的关键业务操作，便于合规追溯与事后审计。
 * <p>
 * 写入路径：OperationLogAspect → LinkedBlockingQueue → OperationLogPersister 异步批量落库。
 */
@Data
@TableName("mo_operation_log")
public class OperationLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作类型（如：支付回调、订单状态变更、退款处理） */
    private String type;

    /** 操作人 ID（来自 UserContextHolder） */
    private Long userId;

    /** 操作人用户名 / 邮箱 */
    private String username;

    /** 客户端 IP */
    private String ip;

    /** 操作详情（SpEL 渲染后） */
    private String detail;

    /** 是否成功 1-成功 0-失败 */
    private Boolean success;

    /** 失败时的错误信息 */
    private String errorMessage;

    /** 操作耗时 ms */
    private Long costMillis;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
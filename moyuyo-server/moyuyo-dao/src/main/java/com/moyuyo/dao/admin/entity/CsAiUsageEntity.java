package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 智能客服调用用量记录（对应 mo_cs_ai_usage 表）
 * <p>
 * 每次调用第三方智能体都会落一条，用于：
 * - 成本核算：按 input/output token 汇总每日花费
 * - 质量排查：失败率与 error_msg 定位第三方异常
 * - 性能监控：duration_ms 观察接口耗时劣化
 */
@Data
@TableName("mo_cs_ai_usage")
public class CsAiUsageEntity {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联会话 id（mo_cs_session.id） */
    private Long sessionId;

    /** 用户 id */
    private Long userId;

    /** 提供方：coze / none */
    private String provider;

    /** 第三方对话 id（扣子 chat_id） */
    private String requestId;

    /** 第三方会话 id（扣子 conversation_id） */
    private String conversationId;

    /** 输入 token 数 */
    private Integer inputTokens;

    /** 输出 token 数 */
    private Integer outputTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    /** 本次调用耗时（毫秒） */
    private Integer durationMs;

    /** 是否成功：1 成功 0 失败 */
    private Integer success;

    /** 失败原因（截断至 500 字符） */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

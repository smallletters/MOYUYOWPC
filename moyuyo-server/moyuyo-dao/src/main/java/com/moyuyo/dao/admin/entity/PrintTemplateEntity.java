package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单打印模板实体（对应 mo_print_template 表）
 * <p>
 * 用于将 OrderPrint.vue 中的"模板示例数据"持久化到数据库，避免刷新页面或换电脑后丢失。
 */
@Data
@TableName("mo_print_template")
public class PrintTemplateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模板编码:PICK/PACK/SHIP/LABEL/SHIPPING_LABEL */
    private String code;

    /** 模板名称 */
    private String name;

    /** 默认纸张规格 */
    private String paperSize;

    /**
     * 模板说明（业务上允许清空为空字符串）。
     * <p>
     * MyBatis-Plus 默认 FieldStrategy.NOT_NULL 会忽略 null 字段，导致"清空说明"请求被吞。
     * 这里标记 updateStrategy=ALWAYS，使 updateById 总是提交该字段（包括 null）。
     * <p>
     * 注：3.5.14 jar 中 FieldStrategy 只有 ALWAYS/NOT_NULL/NOT_EMPTY/DEFAULT/NEVER 五个值，
     * 没有 IGNORED。ALWAYS 等同于 IGNORED 的语义（始终提交字段）。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    /** 是否默认模板 */
    private Boolean isDefault;

    /** 展示顺序 */
    private Integer sortOrder;

    /** 创建人 */
    private String creator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客服绩效实体
 */
@Data
@TableName("mo_cs_performance")
public class CsPerformanceEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 客服姓名 */
    private String agentName;

    /** 部门 */
    private String department;

    /** 累计工单数 */
    private Integer totalTickets;

    /** 今日处理工单 */
    private Integer todayTickets;

    /** 平均响应时间(小时) */
    private BigDecimal avgResponseTime;

    /** 满意度评分(1-5) */
    private BigDecimal satisfactionScore;

    /** 今日在线时长 */
    private String todayOnlineDuration;

    /** ONLINE/OFFLINE/BUSY */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime latestLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 短信发送记录实体
 */
@Data
@TableName("mo_sms_record")
public class SmsRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 手机号 */
    private String phone;

    /** 模板编码 */
    private String templateCode;

    /** 短信内容 */
    private String content;

    /** 参数JSON */
    private String paramsJson;

    /** 渠道：ALIYUN */
    private String channel;

    /** 状态：PENDING/SENT/FAILED */
    private String status;

    /** 失败原因 */
    private String failReason;

    /** 发送时间 */
    private LocalDateTime sendTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

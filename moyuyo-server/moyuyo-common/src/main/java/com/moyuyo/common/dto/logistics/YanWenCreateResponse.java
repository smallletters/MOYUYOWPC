package com.moyuyo.common.dto.logistics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 燕文物流 — 创建运单响应参数
 * <p>
 * 对应接口：express.order.create
 * 文档：https://opendocs.yw56.com.cn/webfile/7250693184987074560/
 * <p>
 * 响应结构：{ success, code, message, data: { waybillNumber, orderNumber } }
 * <p>
 * 注意：内部类命名为 DataEntity 而非 Data —— 避免与 Lombok 的 @Data 注解同名
 * 触发 javac 注解解析歧义（@Data 字段类型在源码里与注解同名会让 IDE / 编译器混淆）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YanWenCreateResponse {

    /** 请求是否成功 */
    private Boolean success;
    /** 响应码（0=成功，>0=业务错误，<0=系统异常） */
    private String code;
    /** 响应文本 */
    private String message;

    /** 实体信息 */
    private DataEntity data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataEntity {
        /** 燕文返回的运单号（必填，写回 mo_order.tracking_number） */
        private String waybillNumber;
        /** 我方传入的订单号（回显） */
        private String orderNumber;
    }
}

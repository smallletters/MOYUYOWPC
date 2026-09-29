package com.moyuyo.common.dto.logistics;

import lombok.Data;

/**
 * 燕文物流 — 打印面单响应结果
 * <p>
 * 对应燕文 API：express.order.label.get 响应结构。
 * 返回的 base64String 是面单 PDF（极少数情况是 PNG）的 base64 编码。
 */
@Data
public class YanWenLabelResponse {

    /** 燕文运单号（与请求一致） */
    private String waybillNumber;

    /** 是否取号成功（true=拿到面单） */
    private Boolean isSuccess;

    /** 失败时的错误信息 */
    private String errorMsg;

    /**
     * 面单文件 base64 字符。
     * 默认是 PDF（application/pdf），使用 atob + btoa 解码后直接 iframe/pdf.js 预览，
     * 也可以 data:application/pdf;base64,xxx 内嵌到页面。
     */
    private String base64String;

    /** 文件类型（PDF / PNG），由 SDK 推断 */
    private String contentType;

    /** 文件大小（字节），供前端预检 */
    private Long sizeBytes;
}
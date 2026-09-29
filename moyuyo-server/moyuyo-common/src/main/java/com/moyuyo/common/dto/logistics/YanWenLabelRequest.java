package com.moyuyo.common.dto.logistics;

import lombok.Data;

/**
 * 燕文物流 — 打印面单请求参数
 * <p>
 * 对应燕文开放平台接口：express.order.label.get
 * 参考文档：https://opendocs.yw56.com.cn/webfile/7250693079613575168/
 * <p>
 * 燕文采用 base64 编码返回面单 PDF/PNG；本类用于包装传给后端的具体运单号。
 */
@Data
public class YanWenLabelRequest {

    /** 燕文运单号（必填，已通过 createOrder 创建的运单） */
    private String waybillNumber;
}
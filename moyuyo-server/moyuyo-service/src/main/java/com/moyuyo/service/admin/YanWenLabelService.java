package com.moyuyo.service.admin;

import com.moyuyo.common.dto.logistics.YanWenLabelResponse;
import com.moyuyo.dao.entity.OrderEntity;

/**
 * 燕文电子面单服务。
 * <p>
 * 按"承运商编码"路由取号逻辑，并处理"承运商未配置 API 凭证 → 回退到全局配置 / 报错"
 * 等场景。
 */
public interface YanWenLabelService {

    /**
     * 取订单对应承运商（燕文）的电子面单。
     * <p>
     * 调用方应在传 {@code carrierId} 时确认订单的承运商编码是 yanwen；
     * 否则应直接走"手动打印运单"的兜底路径。
     *
     * @param order     订单实体（需要 receiverName/Phone/Address 用于调用日志/审计）
     * @param carrierId mo_carrier.id
     * @return 燕文面单响应（含 base64String、contentType）
     */
    YanWenLabelResponse fetchLabel(OrderEntity order, Long carrierId);

    /**
     * 是否已启用燕文 API（用于前端判断按钮文案与可用性）。
     */
    boolean isEnabled();
}
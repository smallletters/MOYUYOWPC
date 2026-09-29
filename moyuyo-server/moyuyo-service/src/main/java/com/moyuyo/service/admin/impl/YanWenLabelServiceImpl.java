package com.moyuyo.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.config.YanWenProperties;
import com.moyuyo.common.dto.logistics.YanWenApiException;
import com.moyuyo.common.dto.logistics.YanWenLabelRequest;
import com.moyuyo.common.dto.logistics.YanWenLabelResponse;
import com.moyuyo.common.logistics.YanWenApiClient;
import com.moyuyo.dao.admin.entity.CarrierEntity;
import com.moyuyo.dao.admin.mapper.CarrierMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.service.admin.YanWenLabelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 燕文电子面单服务实现。
 * <p>
 * 取号凭证优先级：
 *   1. {@code mo_carrier.api_user_id + api_token}（按 carrierId 查到承运商）
 *   2. 回退到 application.yml 的 {@link YanWenProperties} 全局配置
 *   3. 都没配置 → 抛 {@link YanWenApiException} 提示先在承运商配置或全局配置里填凭证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YanWenLabelServiceImpl implements YanWenLabelService {

    private final CarrierMapper carrierMapper;
    private final YanWenProperties yanWenProperties;

    @Override
    public boolean isEnabled() {
        return yanWenProperties.isEnabled();
    }

    @Override
    public YanWenLabelResponse fetchLabel(OrderEntity order, Long carrierId) {
        if (!yanWenProperties.isEnabled()) {
            throw new YanWenApiException("燕文电子面单功能未启用，请在 application.yml 设置 moyuyo.logistics.yanwen.enabled=true");
        }
        if (order == null) {
            throw new IllegalArgumentException("订单不能为空");
        }
        // 当前业务约定：订单表里直接保存了燕文运单号；否则要求手动录入后再触发
        String waybill = order.getTrackingNumber();
        if (waybill == null || waybill.isBlank()) {
            throw new YanWenApiException("订单 " + order.getOrderNo() + " 尚未录入燕文运单号，无法取面单");
        }

        // 解析承运商：优先用 mo_carrier 上的凭证，其次全局配置
        String userId = yanWenProperties.getUserId();
        String apiToken = yanWenProperties.getApiToken();
        String baseUrl = yanWenProperties.getBaseUrl();

        // 修复 P0-2：当 carrierId 未传时，按订单的承运商名称自动解析，
        // 避免前端每次都拿不到 carrierId 导致燕文被错配给顺丰/中通订单。
        CarrierEntity carrier = null;
        if (carrierId != null) {
            carrier = carrierMapper.selectOne(
                    new LambdaQueryWrapper<CarrierEntity>().eq(CarrierEntity::getId, carrierId));
        } else if (order.getShippingCarrier() != null && !order.getShippingCarrier().isBlank()) {
            // 按名称反查（先按 name，再按 code 兜底）
            String carrierName = order.getShippingCarrier().trim();
            carrier = carrierMapper.selectOne(
                    new LambdaQueryWrapper<CarrierEntity>()
                            .eq(CarrierEntity::getName, carrierName)
                            .last("LIMIT 1"));
            if (carrier == null) {
                carrier = carrierMapper.selectOne(
                        new LambdaQueryWrapper<CarrierEntity>()
                                .eq(CarrierEntity::getCode, carrierName.toLowerCase())
                                .last("LIMIT 1"));
            }
        }

        if (carrier != null) {
            // 校验承运商 code 是否为 yanwen（避免误用其他承运商调燕文接口）
            // 注：空 code 视为兼容（早期未配置 code 的承运商），但要求 name 含"燕文"
            String code = carrier.getCode();
            boolean codeIsYanwen = "yanwen".equalsIgnoreCase(code);
            boolean nameContainsYanwen = carrier.getName() != null && carrier.getName().contains("燕文");
            if (code != null && !codeIsYanwen) {
                throw new YanWenApiException("订单承运商【" + carrier.getName() + "】(code=" + code
                        + ") 不是燕文，无法调取燕文电子面单。请先在订单管理把承运商改为燕文。");
            }
            if (code == null && !nameContainsYanwen) {
                throw new YanWenApiException("订单承运商【" + carrier.getName() + "】未配置编码，无法确认是燕文承运商。请先在物流管理→承运商里补充 code=yanwen。");
            }
            // 承运商上的 API 凭证覆盖全局默认
            if (carrier.getApiUserId() != null && !carrier.getApiUserId().isBlank()) {
                userId = carrier.getApiUserId();
            }
            if (carrier.getApiToken() != null && !carrier.getApiToken().isBlank()) {
                apiToken = carrier.getApiToken();
            }
            if (carrier.getApiBaseUrl() != null && !carrier.getApiBaseUrl().isBlank()) {
                baseUrl = carrier.getApiBaseUrl();
            }
        } else if (order.getShippingCarrier() != null && !order.getShippingCarrier().isBlank()) {
            // 订单上写了承运商但 mo_carrier 查不到 → 提示用户先建档
            throw new YanWenApiException("订单承运商【" + order.getShippingCarrier()
                    + "】在 mo_carrier 表里不存在，无法调燕文接口。请先在管理后台→承运商管理中维护对应记录。");
        }

        if (userId == null || userId.isBlank() || apiToken == null || apiToken.isBlank()) {
            throw new YanWenApiException("燕文凭证未配置：请在 mo_carrier 表或 application.yml 的 moyuyo.logistics.yanwen.* 填写 user-id/api-token");
        }

        YanWenApiClient client = new YanWenApiClient(baseUrl, userId, apiToken);
        YanWenLabelRequest req = new YanWenLabelRequest();
        req.setWaybillNumber(waybill);
        YanWenLabelResponse resp = client.getLabel(req);
        log.info("燕文取面单成功：orderNo={}, waybill={}, size={}B, contentType={}",
                order.getOrderNo(), resp.getWaybillNumber(), resp.getSizeBytes(), resp.getContentType());
        return resp;
    }
}
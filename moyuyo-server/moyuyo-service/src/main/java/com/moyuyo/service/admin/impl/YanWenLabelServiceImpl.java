package com.moyuyo.service.admin.impl;

import com.moyuyo.common.config.YanWenProperties;
import com.moyuyo.common.dto.logistics.YanWenApiException;
import com.moyuyo.common.dto.logistics.YanWenLabelRequest;
import com.moyuyo.common.dto.logistics.YanWenLabelResponse;
import com.moyuyo.common.logistics.YanWenApiClient;
import com.moyuyo.dao.admin.entity.CarrierEntity;
import com.moyuyo.dao.admin.mapper.CarrierMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.service.admin.YanWenOrderCreator;
import com.moyuyo.service.admin.YanWenLabelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 燕文电子面单服务实现（精简版）。
 * <p>
 * 关键：核心鉴权 / 拼装请求 / 写回订单逻辑已抽到独立 Bean
 * {@link YanWenOrderCreator}。这样：
 *   1. 跨 Bean 调用走 Spring 代理，{@code @Transactional} 能真正生效
 *   2. fetchLabel 和 createWaybill 共享同一鉴权逻辑，避免规则漂移
 * <p>
 * 本类只负责：① 调燕文 label.get（取面单 PDF）；② 透传到 YanWenOrderCreator。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YanWenLabelServiceImpl implements YanWenLabelService {

    private final YanWenProperties yanWenProperties;
    private final CarrierMapper carrierMapper;
    private final YanWenOrderCreator yanWenOrderCreator;

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
        // P0（路径B）：订单无运单号 → 委托给 YanWenOrderCreator 创建运单（拿到运单号后会写回订单）
        String waybill = order.getTrackingNumber();
        if (waybill == null || waybill.isBlank()) {
            log.info("订单 {} 无运单号，自动调用燕文 createOrder 建运单（路径B）", order.getOrderNo());
            waybill = yanWenOrderCreator.create(order, carrierId);
        }

        // 取面单前再次解析凭证（独立 Bean 已校验过通道 ID；这里直接复用凭证解析）
        // 复用 createOrder 同一份逻辑会重复查 DB，故这里直接用全局 + 承运商 兜底
        String userId = yanWenProperties.getUserId();
        String apiToken = yanWenProperties.getApiToken();
        String baseUrl = yanWenProperties.getBaseUrl();
        CarrierEntity carrier = lookupCarrierForFetch(order, carrierId);
        if (carrier != null) {
            if (carrier.getApiUserId() != null && !carrier.getApiUserId().isBlank()) userId = carrier.getApiUserId();
            if (carrier.getApiToken() != null && !carrier.getApiToken().isBlank()) apiToken = carrier.getApiToken();
            // 仅在 baseUrl 是合法的 https:// 公网地址时才覆盖默认，避免误填 localhost/127.x/内网地址
            // 把请求打到本机 8080（典型事故：测试期把 https://open-fat.yw56.com.cn/api/order 错填成
            // http://localhost:8080/api/order 之类的内网地址，导致 Spring 自身返回 404 "path=/api/orde..."）
            if (carrier.getApiBaseUrl() != null && !carrier.getApiBaseUrl().isBlank()) {
                String override = carrier.getApiBaseUrl().trim();
                if (looksLikePublicHttpsUrl(override)) {
                    baseUrl = override;
                } else {
                    log.warn("忽略承运商【{}】的 apiBaseUrl={}（非 https:// 公网地址，继续使用全局 baseUrl={}）。"
                            + "若需覆盖默认地址，请填写完整 https:// 公网域名。",
                            carrier.getName(), override, baseUrl);
                }
            }
        }
        if (userId == null || userId.isBlank() || apiToken == null || apiToken.isBlank()) {
            throw new YanWenApiException("燕文凭证未配置：请在 mo_carrier 或 application.yml 的 moyuyo.logistics.yanwen.* 填写 user-id/api-token");
        }
        log.debug("燕文取面单 baseUrl={}, waybill={}", baseUrl, waybill);

        YanWenApiClient client = new YanWenApiClient(baseUrl, userId, apiToken);
        YanWenLabelRequest req = new YanWenLabelRequest();
        req.setWaybillNumber(waybill);
        YanWenLabelResponse resp = client.getLabel(req);
        log.info("燕文取面单成功：orderNo={}, waybill={}, size={}B, contentType={}",
                order.getOrderNo(), resp.getWaybillNumber(), resp.getSizeBytes(), resp.getContentType());
        return resp;
    }

    /**
     * P0（路径B）：创建运单（透传到独立 Bean）。
     * <p>
     * 实现放到独立 Bean 是为了绕开 Spring AOP 同类内部调用不走代理的坑。
     */
    @Override
    public String createWaybill(OrderEntity order, Long carrierId) {
        return yanWenOrderCreator.create(order, carrierId);
    }

    /**
     * 取面单路径下的承运商查找 —— 与 createOrder 路径解耦（取面单只关心 API 凭证，不校验是否燕文承运商，
     * 因为运单号已经存在 = 已经创建过运单 = 当时已经校验过）。
     * <p>
     * {@code @SuppressWarnings("null")} 用于压制 JDT 对 MyBatis-Plus {@code LambdaQueryWrapper.eq(SFunction, Object)}
     * 方法引用推断的误报：{@code SFunction<T,?>} 在编译期擦除为 {@code Function<T,?>}，{@code T} 上的
     * {@code @Nonnull} 注解丢失，JDT 重新做 null 检查时报"unchecked conversion"。该项目所有同类写法
     * （{@link YanWenOrderCreator} 等）都加了这个抑制，与既有风格一致。
     */
    @SuppressWarnings("null")
    private CarrierEntity lookupCarrierForFetch(OrderEntity order, Long carrierId) {
        if (carrierId != null) {
            return carrierMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CarrierEntity>()
                            .eq(CarrierEntity::getId, carrierId));
        }
        if (order.getShippingCarrier() == null || order.getShippingCarrier().isBlank()) return null;
        String carrierName = order.getShippingCarrier().trim();
        CarrierEntity carrier = carrierMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CarrierEntity>()
                        .eq(CarrierEntity::getName, carrierName)
                        .last("LIMIT 1"));
        if (carrier == null) {
            carrier = carrierMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CarrierEntity>()
                            .eq(CarrierEntity::getCode, carrierName.toLowerCase())
                            .last("LIMIT 1"));
        }
        return carrier;
    }

    /**
     * 校验"看起来像公网 https URL"：仅在承运商覆盖 baseUrl 时用于过滤掉 localhost/127.x/内网 IP。
     * <p>
     * 仅做粗粒度校验（防御误填，不是安全边界）：必须是 https://，且 host 不是 localhost / 127.x.x.x / 0.0.0.0
     * 等内网/回环地址。这样可以拦住"测试期把 apiBaseUrl 错填成 http://localhost:8080/api/order"
     * 这类事故，避免请求被本机 Spring 自身拦截并返回 404。
     */
    private static boolean looksLikePublicHttpsUrl(String url) {
        if (url == null) return false;
        String s = url.trim().toLowerCase();
        if (!s.startsWith("https://")) return false;
        String host;
        try {
            host = new URI(url).getHost();
        } catch (URISyntaxException e) {
            return false;
        }
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase();
        if (h.equals("localhost")) return false;
        if (h.startsWith("127.")) return false;
        if (h.equals("0.0.0.0") || h.equals("::1") || h.endsWith(".local") || h.endsWith(".localhost")) return false;
        // RFC1918 私网地址：10.x / 172.16~31.x / 192.168.x
        if (h.startsWith("10.")) return false;
        if (h.startsWith("192.168.")) return false;
        if (h.startsWith("172.")) {
            // 解析第二段判断是否在 16~31 区间
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return false;
                } catch (NumberFormatException ignore) {
                    // 非数字（如 172.com）放行
                }
            }
        }
        return true;
    }
}

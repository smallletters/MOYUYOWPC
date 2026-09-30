package com.moyuyo.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.common.config.YanWenProperties;
import com.moyuyo.common.dto.logistics.YanWenApiException;
import com.moyuyo.common.dto.logistics.YanWenCreateRequest;
import com.moyuyo.common.dto.logistics.YanWenCreateResponse;
import com.moyuyo.common.logistics.YanWenApiClient;
import com.moyuyo.dao.admin.entity.CarrierEntity;
import com.moyuyo.dao.admin.mapper.CarrierMapper;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.common.util.CountryResolver;
import com.moyuyo.service.OrderService;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 燕文运单创建器（独立 Service Bean）。
 * <p>
 * 为什么要单独建一个 Service 而不是写在 {@link YanWenLabelServiceImpl} 里？
 * <p>
 * Spring 的 {@code @Transactional} 基于代理 AOP 实现，<b>同类内部的方法调用</b>
 * （如 {@code fetchLabel()} 直接调本类的 {@code createWaybill()}）<b>不会</b>经过代理，
 * 事务注解不生效。把它抽到独立 Bean 后，跨 Bean 调用走 Spring 代理，事务注解才能真正生效。
 * <p>
 * 这个 Bean 只负责：① 调燕文 createOrder；② 写回订单运单号；③ 拼装请求体。
 * 不做长事务 / 外部 HTTP 重活 —— 燕文 HTTP 调用通常 1~5s，故意不开大事务避免连接池耗尽。
 * <p>
 * 写库（{@link OrderMapper#updateById}）单独走自己的短事务，与燕文 HTTP 调用解耦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YanWenOrderCreator {

    private final YanWenProperties yanWenProperties;
    private final CarrierMapper carrierMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CountryResolver countryResolver;
    // 兼容历史订单：调燕文 createOrder 前按 mo_address 补齐收件人快照（防止 "收件人姓名字段不可为空"）
    private final OrderService orderService;

    /**
     * 创建燕文运单并写回订单。
     * <p>
     * 关键流程：
     *   1) 解析承运商 + 凭证（优先 mo_carrier，回退 application.yml）
     *   2) 拼装 createOrder 请求（含 remark ⭐ 自定义字段打印的核心入口）
     *   3) 调燕文 HTTP（不在事务内 —— 避免长事务占连接）
     *   4) 拿到 waybillNumber 后开短事务写回订单
     *
     * @param order     订单实体（传过来的对象会被 setTrackingNumber 写新值）
     * @param carrierId 承运商 id（可空，按 order.shippingCarrier 反查）
     * @return 燕文返回的运单号
     */
    public String create(OrderEntity order, Long carrierId) {
        if (order == null) {
            throw new IllegalArgumentException("订单不能为空");
        }
        // 兼容历史订单：调燕文前按 mo_address 补齐收件人/电话/详细地址快照
        // （mo_order.receiver_* 为空时燕文会返回 "收件人姓名字段不可为空"）
        orderService.fillAddressIfAbsent(order);
        // 第一阶段：解析凭证 + 拼装请求 + 调燕文（不带事务 —— 燕文 HTTP 不能占用长事务）
        ResolvedCarrier rc = resolveCarrierAndCredentials(order, carrierId);
        YanWenCreateRequest req = buildCreateRequest(order, rc.channelId);
        YanWenApiClient client = new YanWenApiClient(rc.baseUrl, rc.userId, rc.apiToken);
        YanWenCreateResponse resp = client.createOrder(req);
        if (resp == null || resp.getData() == null || resp.getData().getWaybillNumber() == null
                || resp.getData().getWaybillNumber().isBlank()) {
            throw new YanWenApiException("燕文创建运单成功但未返回运单号：" + resp);
        }
        String waybill = resp.getData().getWaybillNumber();

        // 第二阶段：写回订单（独立短事务 —— 单独方法，事务注解才能生效）
        writeBackWaybill(order, waybill, rc.carrierName);
        log.info("燕文创建运单成功：orderNo={}, waybill={}, channelId={}",
                order.getOrderNo(), waybill, rc.channelId);
        return waybill;
    }

    /**
     * 第二阶段：写回订单的运单号 + 承运商。
     * <p>
     * 单独方法 + {@code @Transactional}，确保走 Spring 代理生效。
     */
    @Transactional
    public void writeBackWaybill(OrderEntity order, String waybill, String defaultCarrierName) {
        order.setTrackingNumber(waybill);
        if (order.getShippingCarrier() == null || order.getShippingCarrier().isBlank()) {
            order.setShippingCarrier(defaultCarrierName != null ? defaultCarrierName : "燕文物流");
        }
        orderMapper.updateById(order);
    }

    /**
     * 解析承运商 + 凭证：抽出 createWaybill / fetchLabel 共用的鉴权逻辑。
     */
    @SuppressWarnings("null") // 抑制 MyBatis-Plus 3.x @Nonnull T 与 JDT 静态分析差异（覆盖 nullUncheckedConversion 等所有 null 子类别）
    private ResolvedCarrier resolveCarrierAndCredentials(OrderEntity order, Long carrierId) {
        String userId = yanWenProperties.getUserId();
        String apiToken = yanWenProperties.getApiToken();
        String baseUrl = yanWenProperties.getBaseUrl();
        String channelId = yanWenProperties.getChannelId();
        String carrierName = "燕文物流";

        CarrierEntity carrier = null;
        if (carrierId != null) {
            carrier = carrierMapper.selectOne(
                    new LambdaQueryWrapper<CarrierEntity>().eq(CarrierEntity::getId, carrierId));
        } else if (order.getShippingCarrier() != null && !order.getShippingCarrier().isBlank()) {
            String carrierNameToLookup = order.getShippingCarrier().trim();
            carrier = carrierMapper.selectOne(
                    new LambdaQueryWrapper<CarrierEntity>()
                            .eq(CarrierEntity::getName, carrierNameToLookup)
                            .last("LIMIT 1"));
            if (carrier == null) {
                carrier = carrierMapper.selectOne(
                        new LambdaQueryWrapper<CarrierEntity>()
                                .eq(CarrierEntity::getCode, carrierNameToLookup.toLowerCase())
                                .last("LIMIT 1"));
            }
        }

        if (carrier != null) {
            // 校验承运商 code 是否为燕文系（避免误用其他承运商调燕文接口）
            // 兼容所有以 yanwen 开头的 code：
            //   - yanwen（按"一家承运商 = 一个 record"的旧模型）
            //   - yanwen_us / yanwen_uk / yanwen_jp（按国家/产品线拆条的新模型）
            String code = carrier.getCode();
            boolean codeIsYanwen = code != null && code.toLowerCase().startsWith("yanwen");
            boolean nameContainsYanwen = carrier.getName() != null && carrier.getName().contains("燕文");
            if (code != null && !codeIsYanwen) {
                throw new YanWenApiException("订单承运商【" + carrier.getName() + "】(code=" + code
                        + ") 不是燕文，无法调取燕文电子面单。请先在订单管理把承运商改为燕文。");
            }
            if (code == null && !nameContainsYanwen) {
                throw new YanWenApiException("订单承运商【" + carrier.getName() + "】未配置编码，无法确认是燕文承运商。");
            }
            // 承运商上的凭证覆盖全局默认
            if (carrier.getApiUserId() != null && !carrier.getApiUserId().isBlank()) userId = carrier.getApiUserId();
            if (carrier.getApiToken() != null && !carrier.getApiToken().isBlank()) apiToken = carrier.getApiToken();
            // baseUrl 仅在是 https:// 公网地址时覆盖默认，避免被错填成 localhost/127.x
            // 而把请求打到本机 8080（被 Spring 自身拦下并返回 404 "path=/api/orde..."）
            if (carrier.getApiBaseUrl() != null && !carrier.getApiBaseUrl().isBlank()) {
                String override = carrier.getApiBaseUrl().trim();
                if (looksLikePublicHttpsUrl(override)) {
                    baseUrl = override;
                } else {
                    log.warn("忽略承运商【{}】的 apiBaseUrl={}（非 https:// 公网地址，继续使用全局 baseUrl={}）",
                            carrier.getName(), override, baseUrl);
                }
            }
            if (carrier.getChannelId() != null && !carrier.getChannelId().isBlank()) channelId = carrier.getChannelId();
            if (carrier.getName() != null && !carrier.getName().isBlank()) carrierName = carrier.getName();
        } else if (order.getShippingCarrier() != null && !order.getShippingCarrier().isBlank()) {
            // 订单上写了承运商但 mo_carrier 查不到 → 提示用户先建档
            throw new YanWenApiException("订单承运商【" + order.getShippingCarrier()
                    + "】在 mo_carrier 表里不存在，无法调燕文接口。请先在管理后台→承运商管理中维护对应记录。");
        }

        if (userId == null || userId.isBlank() || apiToken == null || apiToken.isBlank()) {
            throw new YanWenApiException("燕文凭证未配置：请在 mo_carrier 或 application.yml 的 moyuyo.logistics.yanwen.* 填写 user-id/api-token");
        }
        if (channelId == null || channelId.isBlank()) {
            throw new YanWenApiException("channelId 未配置：请在 mo_carrier.channelId 或 application.yml 的 moyuyo.logistics.yanwen.channel-id 填写产品编码");
        }
        return new ResolvedCarrier(userId, apiToken, baseUrl, channelId, carrierName);
    }

    /**
     * P0：构造燕文 createOrder 请求体。
     * <p>
     * 关键：
     *   - 国家 / 州 / 城市 / 邮编：从 receiverAddress 字符串里用正则抽取，缺啥用默认占位
     *   - 商品重量：单件默认 500g（跨境电商常见兜底值，避免阻塞下单）
     *   - 包裹尺寸：默认 10x10x10cm
     *   - remark：⭐ 把订单号/商品/买家备注/实付金额 拼成"拣货单信息"，
     *            燕文会在打印标签上显示这块内容 —— 自定义字段打印的核心入口
     */
    private YanWenCreateRequest buildCreateRequest(OrderEntity order, String channelId) {
        YanWenCreateRequest req = new YanWenCreateRequest();
        req.setChannelId(channelId);
        req.setOrderSource("moyuyo");
        req.setOrderNumber(order.getOrderNo());
        req.setSalesPlatform("moyuyo");

        YanWenCreateRequest.ReceiverInfo ri = req.getReceiverInfo();
        ri.setName(truncate(order.getReceiverName(), 50));
        ri.setPhone(truncate(order.getReceiverPhone(), 50));
        AddressParts parts = parseAddress(order.getReceiverAddress());
        // 国家码：用 CountryResolver 解析（yml 配置 alias + 兜底默认）
        // 替代旧的硬编码 "US"，支持中国/英国/加拿大等多国家订单
        ri.setCountry(countryResolver.resolve(order.getReceiverAddress()));
        ri.setState(truncate(parts.state, 50));
        ri.setCity(truncate(parts.city, 50));
        ri.setZipCode(truncate(parts.zip, 50));
        ri.setAddress(truncate(parts.detail, 200));

        List<OrderItemEntity> items = orderItemMapper.selectByOrderId(order.getId());
        int totalQty = 0;
        YanWenCreateRequest.ParcelInfo pi = req.getParcelInfo();
        if (items != null && !items.isEmpty()) {
            for (OrderItemEntity it : items) {
                int qty = it.getQuantity() == null ? 1 : Math.max(1, it.getQuantity());
                totalQty += qty;
                YanWenCreateRequest.ProductItem p = new YanWenCreateRequest.ProductItem();
                String name = it.getProductName() == null ? "Goods" : it.getProductName();
                p.setGoodsNameCh(truncate(name, 200));
                p.setGoodsNameEn(truncate(name, 200)); // 业务无英文品名字段，用中文兜底
                p.setPrice(it.getPrice() == null ? "0.00" : it.getPrice().toPlainString());
                p.setQuantity(qty);
                p.setWeight(DEFAULT_ITEM_WEIGHT_G);
                pi.getProductList().add(p);
            }
        } else {
            // 燕文 productList 至少要有 1 项；空订单给个占位品（避免燕文校验失败）
            // 这种情况极少（应该是测试订单 / 异常数据），但要兜住不让 createWaybill 崩
            YanWenCreateRequest.ProductItem placeholder = new YanWenCreateRequest.ProductItem();
            placeholder.setGoodsNameCh("Goods");
            placeholder.setGoodsNameEn("Goods");
            placeholder.setPrice("0.00");
            placeholder.setQuantity(1);
            placeholder.setWeight(DEFAULT_ITEM_WEIGHT_G);
            pi.getProductList().add(placeholder);
            totalQty = 1;
        }
        pi.setTotalQuantity(Math.max(totalQty, 1));
        pi.setTotalWeight(Math.max(totalQty, 1) * DEFAULT_ITEM_WEIGHT_G);
        pi.setHeight(DEFAULT_PARCEL_SIZE_CM);
        pi.setWidth(DEFAULT_PARCEL_SIZE_CM);
        pi.setLength(DEFAULT_PARCEL_SIZE_CM);
        pi.setHasBattery(0); // 默认无电
        pi.setCurrency(order.getCurrency() == null ? "USD" : order.getCurrency());

        if (order.getSenderName() != null) {
            YanWenCreateRequest.SenderInfo si = req.getSenderInfo();
            si.setName(truncate(order.getSenderName(), 50));
            si.setPhone(truncate(order.getSenderPhone(), 50));
            si.setAddress(truncate(order.getSenderAddress(), 200));
        }

        // ⭐ 自定义字段打印：拼成 remark 字符串
        req.setRemark(buildCustomRemark(order, items));
        return req;
    }

    /**
     * 把订单关键信息拼成"拣货单信息"字符串 —— 自定义字段打印的核心。
     * <p>
     * 拼接规则：订单号 + 商品明细（最多 5 件，超出显示"等N件"）+ 买家备注 + 实付金额。
     * 燕文 remark 限 200 字符，这里做了截断保护。
     */
    private String buildCustomRemark(OrderEntity order, List<OrderItemEntity> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("订单号：").append(order.getOrderNo());
        if (items != null && !items.isEmpty()) {
            sb.append(" | 商品：");
            int displayCount = Math.min(items.size(), 5);
            for (int i = 0; i < displayCount; i++) {
                OrderItemEntity it = items.get(i);
                if (i > 0) sb.append("; ");
                sb.append(it.getProductName() == null ? "?" : it.getProductName())
                        .append(" x").append(it.getQuantity() == null ? 1 : it.getQuantity());
            }
            if (items.size() > 5) sb.append("; 等").append(items.size()).append("件");
        }
        if (order.getRemark() != null && !order.getRemark().isBlank()) {
            sb.append(" | 备注：").append(order.getRemark());
        }
        if (order.getPayAmount() != null) {
            sb.append(" | 金额：").append(order.getPayAmount().toPlainString())
                    .append(order.getCurrency() == null ? "USD" : order.getCurrency());
        }
        return truncate(sb.toString(), 200);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    /**
     * 简易地址解析：从 "xxx, yyy zzz 12345" 格式抽取州/城市/邮编/详细地址。
     * <p>
     * 不做地理编码，仅做关键字抽取。复杂场景建议接入 Google/高德地理编码 API。
     */
    private static final Pattern ZIP_PATTERN = Pattern.compile("\\b(\\d{5}(?:-\\d{4})?)\\b");

    private static AddressParts parseAddress(String raw) {
        AddressParts p = new AddressParts();
        // country 字段已交给 CountryResolver 处理（buildCreateRequest 里直接用 resolver.resolve(...)）
        // 这里不重复解析 —— 燕文请求体的 country 来自 resolver.resolve()，不依赖 parts.country
        p.state = "";
        p.city = "";
        p.zip = "";
        p.detail = raw == null ? "" : raw;
        if (raw == null || raw.isBlank()) return p;
        Matcher m = ZIP_PATTERN.matcher(raw);
        if (m.find()) {
            p.zip = m.group(1);
        }
        String trimmed = raw;
        if (p.zip != null && !p.zip.isEmpty()) {
            trimmed = trimmed.replace(p.zip, "").trim();
        }
        int cut = trimmed.indexOf(',');
        if (cut > 0 && cut < 30) {
            p.city = trimmed.substring(0, cut).trim();
            p.state = trimmed.substring(cut + 1).trim();
        } else {
            cut = trimmed.indexOf(' ');
            if (cut > 0 && cut < 30) {
                p.city = trimmed.substring(0, cut).trim();
                p.state = trimmed.substring(cut + 1).trim();
            } else {
                p.city = trimmed;
            }
        }
        p.detail = trimmed;
        return p;
    }

    private static class AddressParts {
        String state;
        String city;
        String zip;
        String detail;
    }

    private static final int DEFAULT_ITEM_WEIGHT_G = 500;
    private static final int DEFAULT_PARCEL_SIZE_CM = 10;

    /** 解析结果（内部传递用） */
    private static class ResolvedCarrier {
        final String userId;
        final String apiToken;
        final String baseUrl;
        final String channelId;
        final String carrierName;
        ResolvedCarrier(String userId, String apiToken,
                        String baseUrl, String channelId, String carrierName) {
            this.userId = userId;
            this.apiToken = apiToken;
            this.baseUrl = baseUrl;
            this.channelId = channelId;
            this.carrierName = carrierName;
        }
    }

    /**
     * 与 YanWenLabelServiceImpl#looksLikePublicHttpsUrl 同语义：粗粒度校验是否为公网 https URL。
     * 用于拒绝覆盖默认 baseUrl 的 localhost/127.x/私网地址，避免请求打到本机 8080。
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
        if (h.startsWith("10.")) return false;
        if (h.startsWith("192.168.")) return false;
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return false;
                } catch (NumberFormatException ignore) {
                    // 非数字放行
                }
            }
        }
        return true;
    }
}

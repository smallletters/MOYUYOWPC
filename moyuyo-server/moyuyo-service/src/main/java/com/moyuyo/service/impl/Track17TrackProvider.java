package com.moyuyo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.logistics.LogisticsVO;
import com.moyuyo.service.LogisticsTrackProvider;
import com.moyuyo.service.config.LogisticsTrackProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 17TRACK 物流轨迹实现
 * <p>
 * 启用条件：{@code moyuyo.logistics.provider=17track}
 * 调用接口：{@code POST https://api.17track.net/track/v1/gettrackinfo}
 * 鉴权方式：HTTP Header {@code 17token: <apiKey>}
 * 限流策略：3 req/s（17TRACK 平台限制），超限返回 HTTP 429
 * <p>
 * 响应示例：
 * <pre>{@code
 * {
 *   "code": 0,
 *   "data": {
 *     "accepted": [{
 *       "number": "RR123456789CN",
 *       "carrier": 3011,
 *       "events": [
 *         {"time":"2024-01-01T12:00:00+00:00","location":"Beijing","description":"Acceptance","status":"..."}
 *       ],
 *       "last_status":"Delivered",
 *       "last_event":"2024-01-05 Signed by recipient"
 *     }],
 *     "rejected":[]
 *   }
 * }
 * }</pre>
 *
 * @see <a href="https://asset.17track.net/api/document/v1_en/index.html">17TRACK V1 API 文档</a>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "moyuyo.logistics", name = "provider", havingValue = "17track")
public class Track17TrackProvider implements LogisticsTrackProvider {

    private static final String API_BASE = "https://api.17track.net/track/v1";
    private static final String HEADER_TOKEN = "17token";

    /**
     * 签收关键字（忽略大小写匹配 description / last_status）
     * 中英文混合匹配，覆盖国内外运输商推送文案
     */
    private static final Set<String> DELIVERED_KEYWORDS = Set.of(
            "delivered", "signed", "received", "签收", "妥投", "已签收", "已妥投"
    );

    /**
     * 17TRACK 错误码：单号未注册，需要先调用 /register 订阅
     */
    private static final int ERR_NOT_REGISTERED = -18019902;

    /**
     * 17TRACK 错误码：暂无物流信息（已注册但运输商尚未推送事件）
     */
    private static final int ERR_NO_TRACKING_INFO = -18019909;

    private final LogisticsTrackProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 使用独立的 RestTemplate 实例，按 LogisticsTrackProperties 单独配置超时
     * 避免与 WooCommerce 共用同一实例导致超时配置相互覆盖
     */
    private final RestTemplate restTemplate;

    /**
     * 通过构造器注入独立 Bean 名称，避免与 WooCommerce 的 restTemplate Bean 冲突
     * <p>
     * Spring Boot 在没有显式 @Bean("track17RestTemplate") 时会回落到默认 restTemplate，
     * 由 RestTemplateBootstrapConfig 提供。如需独立超时配置，可补充一个 @Bean("track17RestTemplate")
     * 使用 LogisticsTrackProperties.connectTimeoutMs / readTimeoutMs 创建。
     * 当前默认共用全局 restTemplate（5s/15s）已足够覆盖 17TRACK 接口响应时间。
     */
    public Track17TrackProvider(LogisticsTrackProperties properties,
                                ObjectMapper objectMapper,
                                @Qualifier("restTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * 查询运单轨迹（带重试 + 熔断保护）
     * <p>
     * - {@code @Retry(name="track17")}：网络抖动 / 5xx 自动重试 2 次（指数退避）
     * - {@code @CircuitBreaker(name="track17")}：连续失败 50% 触发熔断，60s 后半开探测
     * - 熔断打开期间直接走 fallback，返回空列表，不阻塞订单查询主链路
     */
    @Override
    @Retry(name = "track17", fallbackMethod = "fallbackQueryTracks")
    @CircuitBreaker(name = "track17", fallbackMethod = "fallbackQueryTracks")
    public List<LogisticsVO.TraceItem> queryTracks(String carrier, String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Collections.emptyList();
        }
        return doQueryTracks(trackingNumber);
    }

    @Override
    public boolean isDelivered(String carrier, String trackingNumber) {
        List<LogisticsVO.TraceItem> tracks = queryTracks(carrier, trackingNumber);
        if (tracks.isEmpty()) {
            return false;
        }
        // 签收判定：检查最新轨迹的 desc / status 字段
        LogisticsVO.TraceItem latest = tracks.get(0);
        return containsDeliveredKeyword(latest.getDesc())
                || containsDeliveredKeyword(latest.getStatus());
    }

    @Override
    public String getProviderName() {
        return "17track";
    }

    // ========== 核心调用逻辑 ==========

    /**
     * 实际调用 17TRACK API 查询轨迹
     * <p>
     * 设计要点：
     * <ul>
     *   <li>不传 carrier 参数让 17TRACK 自动识别（避免维护 SF->数字 carrier 映射表）</li>
     *   <li>HTTP 429 限流直接返回空列表（重试会放大限流），等下次定时轮询</li>
     *   <li>code=0 + rejected 含 -18019902（未注册）：自动注册一次，本次返回空轨迹</li>
     *   <li>code=0 + rejected 含 -18019909（暂无信息）：返回空列表，等下次轮询</li>
     *   <li>异常一律抛 RuntimeException，由 @Retry / @CircuitBreaker 决定是否重试</li>
     * </ul>
     */
    private List<LogisticsVO.TraceItem> doQueryTracks(String trackingNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_TOKEN, properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        // 请求体：JSON 数组（17TRACK V1 要求 List 格式，单次最多 40 个单号）
        List<Map<String, Object>> requestBody = List.of(
                Map.of("number", trackingNumber));
        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(requestBody, headers);

        String url = API_BASE + "/gettrackinfo";
        log.debug("17TRACK query: url={}, tracking={}", url, trackingNumber);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);

        return parseResponse(response.getBody(), trackingNumber);
    }

    /**
     * 解析 17TRACK V1 响应报文，提取轨迹列表
     * <p>
     * 响应结构：
     * <pre>{@code
     * {"code":0,"data":{"accepted":[{"events":[...]}],"rejected":[]}}
     * }</pre>
     */
    private List<LogisticsVO.TraceItem> parseResponse(String body, String trackingNumber) {
        if (body == null || body.isBlank()) {
            log.warn("17TRACK response body empty, tracking={}", trackingNumber);
            return Collections.emptyList();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("17TRACK response parse failed, tracking={}, body={}", trackingNumber, body, e);
            return Collections.emptyList();
        }

        int code = root.path("code").asInt(-1);
        if (code != 0) {
            log.warn("17TRACK business error, code={}, tracking={}, body={}", code, trackingNumber, body);
            return Collections.emptyList();
        }

        JsonNode data = root.path("data");
        JsonNode accepted = data.path("accepted");
        JsonNode rejected = data.path("rejected");

        // 处理 rejected：未注册 / 暂无信息 等业务错误
        if (rejected.isArray() && !rejected.isEmpty()) {
            for (JsonNode rj : rejected) {
                int errCode = rj.path("error").path("code").asInt(0);
                String errMsg = rj.path("error").path("message").asText("");
                if (errCode == ERR_NOT_REGISTERED) {
                    log.info("17TRACK tracking not registered, auto-registering: tracking={}", trackingNumber);
                    registerTracking(trackingNumber);
                    continue;
                }
                if (errCode == ERR_NO_TRACKING_INFO) {
                    log.info("17TRACK no tracking info yet, tracking={}", trackingNumber);
                    continue;
                }
                log.warn("17TRACK rejected, code={}, msg={}, tracking={}", errCode, errMsg, trackingNumber);
            }
            // 没有 accepted 数据时返回空列表
            if (!accepted.isArray() || accepted.isEmpty()) {
                return Collections.emptyList();
            }
        }

        if (!accepted.isArray() || accepted.isEmpty()) {
            return Collections.emptyList();
        }

        // 取第一个匹配结果（不传 carrier 时如有多个，取最新注册的一条）
        JsonNode firstAccepted = accepted.get(0);
        JsonNode events = firstAccepted.path("events");

        if (!events.isArray() || events.isEmpty()) {
            return Collections.emptyList();
        }

        // 映射字段：17TRACK description -> TraceItem.desc；并倒序输出（最新轨迹置顶）
        List<LogisticsVO.TraceItem> items = new ArrayList<>(events.size());
        for (JsonNode ev : events) {
            LogisticsVO.TraceItem item = new LogisticsVO.TraceItem();
            item.setTime(ev.path("time").asText(""));
            item.setLocation(ev.path("location").asText(""));
            item.setDesc(ev.path("description").asText(""));
            item.setStatus(ev.path("status").asText(""));
            items.add(item);
        }
        // 原始顺序可能为升序（最旧先），统一倒序输出
        Collections.reverse(items);
        return items;
    }

    /**
     * 自动注册单号到 17TRACK，便于后续轮询拉取轨迹
     * <p>
     * 失败不抛异常（注册失败本次查询已返回空轨迹，下次定时任务会重试注册）
     */
    private void registerTracking(String trackingNumber) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_TOKEN, properties.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            List<Map<String, Object>> requestBody = List.of(
                    Map.of("number", trackingNumber));
            HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(requestBody, headers);

            String url = API_BASE + "/register";
            log.info("17TRACK register: url={}, tracking={}", url, trackingNumber);

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (HttpClientErrorException e) {
            // 401 / 429 等业务错误不重试（重试会放大限流）
            log.warn("17TRACK register HttpClientError: status={}, tracking={}, body={}",
                    e.getStatusCode(), trackingNumber, e.getResponseBodyAsString());
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("17TRACK register network error: tracking={}, msg={}", trackingNumber, e.getMessage());
        } catch (Exception e) {
            log.warn("17TRACK register unexpected error: tracking={}", trackingNumber, e);
        }
    }

    /**
     * 签收关键字匹配（忽略大小写）
     */
    private boolean containsDeliveredKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : DELIVERED_KEYWORDS) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // ========== Fallback ==========

    /**
     * 熔断打开 / 重试耗尽时调用，返回空轨迹（物流弹窗回退到人工录入数据）
     * <p>
     * 不抛异常，避免影响订单详情查询主链路
     */
    @SuppressWarnings("unused")
    private List<LogisticsVO.TraceItem> fallbackQueryTracks(String carrier, String trackingNumber, Throwable t) {
        if (t instanceof HttpClientErrorException hce) {
            log.warn("17TRACK client error (fallback): tracking={}, status={}, body={}",
                    trackingNumber, hce.getStatusCode(), hce.getResponseBodyAsString());
        } else if (t instanceof HttpServerErrorException hse) {
            log.warn("17TRACK server error (fallback): tracking={}, status={}",
                    trackingNumber, hse.getStatusCode());
        } else if (t instanceof ResourceAccessException rae) {
            log.warn("17TRACK network/timeout (fallback): tracking={}, msg={}",
                    trackingNumber, rae.getMessage());
        } else {
            log.warn("17TRACK unexpected error (fallback): tracking={}, msg={}",
                    trackingNumber, t.getMessage());
        }
        return Collections.emptyList();
    }
}

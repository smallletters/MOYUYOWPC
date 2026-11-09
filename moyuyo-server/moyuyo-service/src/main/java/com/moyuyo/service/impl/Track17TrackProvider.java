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
     *   <li>HTTP 429 限流：直接吞掉异常返回空列表，避免 @Retry 重试放大限流</li>
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

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
        } catch (HttpClientErrorException.TooManyRequests tooManyRequests) {
            // 429 是平台限流信号：重试只会继续被拒并拖垮熔断统计，
            // 这里直接返回空轨迹，等下一轮轮询（轮询任务自带 350ms 逐笔节流）
            log.warn("17TRACK rate limited (429), skip and retry on next poll, tracking={}", trackingNumber);
            return Collections.emptyList();
        }

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

        // 17TRACK 的权威状态字段：accepted[].last_status（如 Delivered / Undelivered）。
        // events 与紧凑 z1 格式不一定带 status，忽略它会让自动签收只能依赖描述关键词（本地化描述会漏判）。
        String lastStatus = firstAccepted.path("last_status").asText("");

        // 兼容新版 events 数组格式（time/location/description/status）
        JsonNode events = firstAccepted.path("events");
        if (events.isArray() && !events.isEmpty()) {
            List<LogisticsVO.TraceItem> items = new ArrayList<>(events.size());
            for (JsonNode ev : events) {
                LogisticsVO.TraceItem item = new LogisticsVO.TraceItem();
                item.setTime(ev.path("time").asText(""));
                item.setLocation(ev.path("location").asText(""));
                item.setDesc(ev.path("description").asText(""));
                item.setStatus(ev.path("status").asText(""));
                items.add(item);
            }
            // 统一按真实时间倒序（最新在前）：直接比较字符串在时区/格式不同时会排错
            items.sort(Comparator.comparingLong((LogisticsVO.TraceItem t) -> timeSortKey(t.getTime())).reversed());
            applyLastStatus(items, lastStatus);
            return items;
        }

        // V1 API 实际返回的紧凑格式：track.z1 为轨迹数组（a=时间/c=地点/z=描述）
        JsonNode track = firstAccepted.path("track");
        JsonNode z1 = track.path("z1");
        if (z1.isArray() && !z1.isEmpty()) {
            List<LogisticsVO.TraceItem> items = new ArrayList<>(z1.size());
            for (JsonNode ev : z1) {
                LogisticsVO.TraceItem item = new LogisticsVO.TraceItem();
                item.setTime(ev.path("a").asText(""));
                item.setLocation(ev.path("c").asText(""));
                item.setDesc(ev.path("z").asText(""));
                items.add(item);
            }
            // z1 通常已倒序，但仍统一排序：顺序变化会导致首条不是最新，签收判定取错事件
            items.sort(Comparator.comparingLong((LogisticsVO.TraceItem t) -> timeSortKey(t.getTime())).reversed());
            applyLastStatus(items, lastStatus);
            return items;
        }

        // 兜底：仅返回最新一条 z0 时也能展示
        JsonNode z0 = track.path("z0");
        if (z0.isObject()) {
            LogisticsVO.TraceItem item = new LogisticsVO.TraceItem();
            item.setTime(z0.path("a").asText(""));
            item.setLocation(z0.path("c").asText(""));
            item.setDesc(z0.path("z").asText(""));
            item.setStatus(lastStatus);
            return List.of(item);
        }

        return Collections.emptyList();
    }

    /**
     * 用 17TRACK 的 last_status 补齐"最新一条"轨迹的状态
     * <p>
     * last_status 是平台侧的权威状态（Delivered / Undelivered / InTransit 等），
     * 而 events / z1 明细里可能没有 status 字段；不补这一步，自动签收只能依赖描述关键词。
     */
    private void applyLastStatus(List<LogisticsVO.TraceItem> items, String lastStatus) {
        if (items.isEmpty() || lastStatus == null || lastStatus.isBlank()) {
            return;
        }
        // 仅覆盖空状态，避免丢失明细中更精确的状态（如 "Delivered" 细分事件）
        if (items.get(0).getStatus() == null || items.get(0).getStatus().isBlank()) {
            items.get(0).setStatus(lastStatus);
        }
    }

    /**
     * 轨迹时间排序键（毫秒时间戳）
     * <p>
     * 17TRACK 同时存在 ISO8601 带时区（events.time）与 "yyyy-MM-dd HH:mm(:ss)" 本地时间（z1.a）两种格式，
     * 直接按字符串排序会在时区/格式混排时把最新事件排到后面，进而导致签收判定取错事件。
     * 无法解析的时间返回 Long.MIN_VALUE（视为最旧，倒序后排在末尾）。
     */
    private static long timeSortKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return Long.MIN_VALUE;
        }
        String trimmed = raw.trim();
        try {
            // ISO8601 带时区偏移，如 2024-01-01T12:00:00+00:00
            return java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli();
        } catch (Exception ignore) {
            // 继续尝试本地时间格式
        }
        try {
            // "2024-01-01 12:00:00" / "2024-01-01 12:00"
            String normalized = trimmed.replace(' ', 'T');
            return java.time.LocalDateTime.parse(normalized)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignore) {
            return Long.MIN_VALUE;
        }
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

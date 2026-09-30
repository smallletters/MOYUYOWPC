package com.moyuyo.common.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.logistics.YanWenApiException;
import com.moyuyo.common.dto.logistics.YanWenCountryListResponse;
import com.moyuyo.common.dto.logistics.YanWenCreateRequest;
import com.moyuyo.common.dto.logistics.YanWenCreateResponse;
import com.moyuyo.common.dto.logistics.YanWenLabelRequest;
import com.moyuyo.common.dto.logistics.YanWenLabelResponse;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 燕文物流开放平台 API 客户端（轻量级 SDK）。
 * <p>
 * 参考文档：
 * <ul>
 *   <li>打 印 面 单：https://opendocs.yw56.com.cn/webfile/7250693079613575168/</li>
 *   <li>查 询 运 单：https://opendocs.yw56.com.cn/webfile/6993834083654569984/</li>
 * </ul>
 * 公共参数：所有接口使用 POST，Content-Type=application/json，签名 MD5 32 位小写。
 * <p>
 * 签名规则（按官方）：
 *   1. 按字典序拼接：user_id+data+format+method+timestamp+version
 *   2. 头尾拼接 apitoken：MD5( apitoken + 步骤1 + apitoken )
 *   3. 32 位小写。
 * <p>
 * 本类不依赖第三方 HTTP 客户端，使用 JDK 11+ 自带的 {@link HttpClient}，
 * 避免引入新依赖。
 */
@Slf4j
public class YanWenApiClient {

    /** 默认 API 地址：正式环境。测试环境可改为 https://open-fat.yw56.com.cn/api/order */
    public static final String DEFAULT_BASE_URL = "https://open.yw56.com.cn/api/order";

    /** 默认版本号（燕文官方 V1.0） */
    public static final String DEFAULT_VERSION = "V1.0";

    /** 默认报文格式：json */
    public static final String DEFAULT_FORMAT = "json";

    /** 请求方法：打 印 面 单 */
    public static final String METHOD_GET_LABEL = "express.order.label.get";

    /** 请求方法：创建运单（用于无运单号订单先建运单再取面单） */
    public static final String METHOD_CREATE_ORDER = "express.order.create";

    /** 请求方法：查询运单详情（暂未在本类暴露，便于后续扩展） */
    public static final String METHOD_GET_ORDER = "express.order.get";

    /** 请求方法：查询燕文通达国家列表（启动期预热缓存） */
    public static final String METHOD_GET_COUNTRY_LIST = "common.country.getlist";

    private final String baseUrl;
    private final String userId;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造燕文 API 客户端。
     *
     * @param baseUrl   接口地址（正式/测试环境），传 null 时使用 {@link #DEFAULT_BASE_URL}
     * @param userId    燕文客户号（user_id）
     * @param apiToken  燕文秘钥 apitoken
     */
    public YanWenApiClient(String baseUrl, String userId, String apiToken) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("燕文 userId 不能为空");
        }
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("燕文 apiToken 不能为空");
        }
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        this.userId = userId;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用 express.order.create 创建燕文运单（用于无运单号订单先建运单再取面单）。
     * <p>
     * 请求 data 是嵌套对象（receiverInfo/parcelInfo/productList/senderInfo），
     * 用 Jackson 序列化整个请求对象为 JSON 字符串，避免手工拼装漏字段或转义错误。
     * <p>
     * 响应成功后会拿到 waybillNumber，写回订单表后再调 getLabel 取面单。
     *
     * @param request 创建运单请求（含 channelId / orderNumber / 收件人 / 商品 / 发件人 等）
     * @return 燕文响应（成功时 data.waybillNumber 非空）
     * @throws YanWenApiException 业务错误（code != 0）
     */
    public YanWenCreateResponse createOrder(YanWenCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getChannelId() == null || request.getChannelId().isBlank()) {
            throw new IllegalArgumentException("channelId 不能为空（请在 mo_carrier.channelId 配置）");
        }
        if (request.getOrderNumber() == null || request.getOrderNumber().isBlank()) {
            throw new IllegalArgumentException("orderNumber 不能为空");
        }
        // 序列化请求对象为 JSON 字符串
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new YanWenApiException("序列化 createOrder 请求失败：" + e.getMessage(), e);
        }
        String rawResponse = invoke(METHOD_CREATE_ORDER, dataJson);

        YanWenCreateResponse resp = new YanWenCreateResponse();
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            resp.setSuccess(root.path("success").asBoolean(false));
            resp.setCode(root.path("code").asText(""));
            resp.setMessage(root.path("message").asText(""));

            boolean success = Boolean.TRUE.equals(resp.getSuccess());
            if (!success) {
                String err = "燕文创建运单失败：" + (resp.getMessage().isEmpty() ? resp.getCode() : resp.getMessage())
                        + " (orderNumber=" + request.getOrderNumber() + ", code=" + resp.getCode() + ")";
                log.warn(err);
                throw new YanWenApiException(resp.getCode(), err);
            }
            JsonNode data = root.path("data");
            YanWenCreateResponse.DataEntity dataObj = new YanWenCreateResponse.DataEntity();
            dataObj.setWaybillNumber(data.path("waybillNumber").asText(""));
            dataObj.setOrderNumber(data.path("orderNumber").asText(request.getOrderNumber()));
            resp.setData(dataObj);
            return resp;
        } catch (YanWenApiException e) {
            throw e;
        } catch (Exception e) {
            throw new YanWenApiException("解析燕文 createOrder 响应失败：" + e.getMessage(), e);
        }
    }

    /**
     * 调用 common.country.getlist 拿燕文通达国家列表。
     * <p>
     * 响应是数组（data: [...]），每条 { id, code, nameCh, nameEn }。
     * 用于启动期预热缓存 + 业务 CountryResolver 做精确中文名匹配。
     * <p>
     * 调用方应在 catch 里降级（不阻塞业务）：缓存为空时 CountryResolver 走 yml aliases 兜底。
     */
    public YanWenCountryListResponse getCountryList() {
        String rawResponse = invoke(METHOD_GET_COUNTRY_LIST, "{}");
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            YanWenCountryListResponse resp = new YanWenCountryListResponse();
            resp.setSuccess(root.path("success").asBoolean(false));
            resp.setCode(root.path("code").asText(""));
            resp.setMessage(root.path("message").asText(""));
            if (!Boolean.TRUE.equals(resp.getSuccess())) {
                String err = "燕文 getCountryList 失败：" + (resp.getMessage().isEmpty() ? resp.getCode() : resp.getMessage());
                log.warn(err);
                throw new YanWenApiException(resp.getCode(), err);
            }
            JsonNode data = root.path("data");
            if (data != null && data.isArray()) {
                for (JsonNode node : data) {
                    YanWenCountryListResponse.CountryItem item =
                            new YanWenCountryListResponse.CountryItem();
                    item.setId(node.path("id").asText(""));
                    item.setCode(node.path("code").asText(""));
                    item.setNameCh(node.path("nameCh").asText(""));
                    item.setNameEn(node.path("nameEn").asText(""));
                    resp.getData().add(item);
                }
            }
            log.info("燕文 getCountryList 成功：{} 个国家", resp.getData().size());
            return resp;
        } catch (YanWenApiException e) {
            throw e;
        } catch (Exception e) {
            throw new YanWenApiException("解析燕文 getCountryList 响应失败：" + e.getMessage(), e);
        }
    }

    /**
     * 调用 express.order.label.get 取运单的面单 PDF/PNG。
     * <p>
     * 燕文返回的是 base64String（PDF），本方法会同步推断 contentType 与 sizeBytes，
     * 方便前端直接 data:application/pdf;base64,xxx 预览。
     */
    public YanWenLabelResponse getLabel(YanWenLabelRequest request) {
        if (request == null || request.getWaybillNumber() == null || request.getWaybillNumber().isBlank()) {
            throw new IllegalArgumentException("运单号 waybillNumber 不能为空");
        }
        String waybillNo = request.getWaybillNumber().trim();
        String dataJson = "{\"waybillNumber\":\"" + escapeJson(waybillNo) + "\"}";
        String rawResponse = invoke(METHOD_GET_LABEL, dataJson);

        YanWenLabelResponse resp = new YanWenLabelResponse();
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            boolean success = root.path("success").asBoolean(false);
            String code = root.path("code").asText("");
            String message = root.path("message").asText("");

            if (!success) {
                String err = "燕文取面单失败：" + (message.isEmpty() ? code : message)
                        + " (waybill=" + waybillNo + ", code=" + code + ")";
                log.warn(err);
                throw new YanWenApiException(code, err);
            }
            JsonNode data = root.path("data");
            resp.setWaybillNumber(data.path("waybillNumber").asText(waybillNo));
            resp.setIsSuccess(data.path("isSuccess").asBoolean(true));
            resp.setErrorMsg(data.path("errorMsg").asText(""));
            String base64 = data.path("base64String").asText("");
            if (base64.isEmpty()) {
                throw new YanWenApiException(code, "燕文返回 base64String 为空");
            }
            resp.setBase64String(base64);
            // 燕文面单默认是 PDF；个别渠道可能是 PNG。这里以 PDF 前缀判断。
            String contentType = detectContentType(base64);
            resp.setContentType(contentType);
            resp.setSizeBytes((long) base64.length());
            return resp;
        } catch (YanWenApiException e) {
            throw e;
        } catch (Exception e) {
            throw new YanWenApiException("解析燕文响应失败：" + e.getMessage(), e);
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 调用燕文任意 method。
     *
     * @param method   接口方法名（如 express.order.label.get）
     * @param dataJson 请求体（已经是 JSON 字符串）
     * @return 燕文原始响应 JSON 字符串
     */
    private String invoke(String method, String dataJson) {
        long timestamp = System.currentTimeMillis();
        // 签名：apitoken + user_id + data + format + method + timestamp + version + apitoken
        String signSource = apiToken + userId + dataJson + DEFAULT_FORMAT + method + timestamp + DEFAULT_VERSION + apiToken;
        String sign = md5Lower32(signSource);

        String url = baseUrl
                + "?user_id=" + urlEncode(userId)
                + "&method=" + urlEncode(method)
                + "&format=" + DEFAULT_FORMAT
                + "&timestamp=" + timestamp
                + "&sign=" + sign
                + "&version=" + DEFAULT_VERSION;

        // P1 调试日志：仅在 DEBUG 开启时打印（部署后保持安静）
        if (log.isDebugEnabled()) {
            log.debug("燕文请求 url=\"{}\", baseUrl.length={}, method={}", url, baseUrl == null ? -1 : baseUrl.length(), method);
        }

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(dataJson, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = httpResp.body();
            if (httpResp.statusCode() / 100 != 2) {
                throw new YanWenApiException("燕文 HTTP " + httpResp.statusCode() + "：" + body);
            }
            log.debug("燕文 {} 响应: status={}, bodyLen={}", method, httpResp.statusCode(), body == null ? 0 : body.length());
            return body == null ? "" : body;
        } catch (YanWenApiException e) {
            throw e;
        } catch (Exception e) {
            throw new YanWenApiException("调用燕文 API 失败：" + e.getMessage(), e);
        }
    }

    /** MD5 32 位小写 */
    private static String md5Lower32(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("MD5 不可用：" + e.getMessage(), e);
        }
    }

    /** 简单 JSON 字符串转义（仅处理 " 与 \，避免在 data 里被破坏） */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else sb.append(c);
        }
        return sb.toString();
    }

    /** 简易 URL 编码（Java 11 URI 内置方案） */
    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * 通过 base64 头部 magic bytes 推断文件类型。
     * 燕文面单默认是 PDF（JVBERi0...）；少数渠道可能返回 PNG（iVBOR...）。
     */
    private static String detectContentType(String base64) {
        if (base64 == null || base64.length() < 16) return "application/pdf";
        String head = base64.substring(0, Math.min(20, base64.length()));
        if (head.startsWith("JVBERi")) return "application/pdf";
        if (head.startsWith("iVBOR")) return "image/png";
        if (head.startsWith("/9j/")) return "image/jpeg";
        return "application/pdf";
    }
}
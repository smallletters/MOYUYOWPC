package com.moyuyo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyuyo.common.dto.geo.GeoPlaceVO;
import com.moyuyo.service.GeoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Google Places / Geocoding 后端代理。
 * Key 仅存在于服务端环境变量（GOOGLE_MAPS_API_KEY），不向前端暴露。
 * 坐标统一按 WGS84（浏览器/uni.getLocation 的 wgs84 输出），与 Google API 一致。
 */
@Slf4j
@Service
public class GeoServiceImpl implements GeoService {

  private static final String PLACES_ENDPOINT = "https://maps.googleapis.com/maps/api/place/nearbysearch/json";
  private static final String GEOCODE_ENDPOINT = "https://maps.googleapis.com/maps/api/geocode/json";
  private static final String OK = "OK";
  private static final String ZERO_RESULTS = "ZERO_RESULTS";

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String googleMapsKey;

  public GeoServiceImpl(
      @Qualifier("restTemplate") RestTemplate restTemplate,
      ObjectMapper objectMapper,
      @Value("${GOOGLE_MAPS_API_KEY:}") String googleMapsKey) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.googleMapsKey = googleMapsKey == null ? "" : googleMapsKey.trim();
  }

  @Override
  public List<GeoPlaceVO> searchPlaces(String keyword, double lat, double lng, int radius) {
    requireKey();
    StringBuilder url = new StringBuilder(PLACES_ENDPOINT)
        .append("?location=").append(formatCoord(lat)).append(',').append(formatCoord(lng))
        .append("&radius=").append(radius)
        .append("&language=en")
        .append("&key=").append(googleMapsKey);
    if (keyword != null && !keyword.isBlank()) {
      url.append("&keyword=").append(urlEncode(keyword.trim()));
    }

    JsonNode root = callGoogle(url.toString());
    String status = root.path("status").asText("");
    // ZERO_RESULTS：关键词附近没有匹配地点，返回空列表而非报错
    if (ZERO_RESULTS.equals(status)) {
      return Collections.emptyList();
    }
    ensureOk(root, status);

    JsonNode results = root.path("results");
    if (!results.isArray()) {
      return Collections.emptyList();
    }
    List<GeoPlaceVO> places = new ArrayList<>();
    for (JsonNode item : results) {
      GeoPlaceVO vo = new GeoPlaceVO();
      vo.setName(item.path("name").asText(""));
      vo.setAddress(item.path("formatted_address").asText(""));
      JsonNode location = item.path("geometry").path("location");
      if (location.isObject()) {
        vo.setLat(location.path("lat").asDouble(0));
        vo.setLng(location.path("lng").asDouble(0));
      }
      // 过滤掉连名称都没有的异常返回
      if (!vo.getName().isEmpty() || !vo.getAddress().isEmpty()) {
        places.add(vo);
      }
    }
    return places;
  }

  @Override
  public String reverseGeocode(double lat, double lng) {
    requireKey();
    String url = GEOCODE_ENDPOINT
        + "?latlng=" + formatCoord(lat) + ',' + formatCoord(lng)
        + "&language=en&key=" + googleMapsKey;
    JsonNode root = callGoogle(url);
    String status = root.path("status").asText("");
    if (ZERO_RESULTS.equals(status)) {
      throw new IllegalArgumentException("未找到该位置的地址");
    }
    ensureOk(root, status);
    JsonNode first = root.path("results").path(0);
    String address = first.path("formatted_address").asText("");
    if (address.isEmpty()) {
      throw new IllegalArgumentException("未找到该位置的地址");
    }
    return address;
  }

  private void requireKey() {
    if (googleMapsKey.isEmpty()) {
      throw new IllegalArgumentException("地图服务未配置，请联系管理员（GOOGLE_MAPS_API_KEY）");
    }
  }

  /** 请求 Google，并把 HTTP/解析异常统一翻译为用户可读错误 */
  private JsonNode callGoogle(String url) {
    String body;
    try {
      body = restTemplate.getForObject(url, String.class);
    } catch (RestClientException e) {
      log.warn("[geo] upstream request failed: {}", e.getMessage());
      throw new IllegalArgumentException("地图服务暂时不可用，请稍后再试");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("地图服务返回为空，请稍后再试");
    }
    try {
      return objectMapper.readTree(body);
    } catch (Exception e) {
      log.warn("[geo] parse upstream response failed", e);
      throw new IllegalArgumentException("地图服务返回异常，请稍后再试");
    }
  }

  private void ensureOk(JsonNode root, String status) {
    if (!OK.equals(status)) {
      String reason = root.path("error_message").asText("");
      log.warn("[geo] google status not ok: status={}, reason={}", status, reason);
      throw new IllegalArgumentException("地图服务请求被拒绝，请稍后再试");
    }
  }

  private String formatCoord(double v) {
    // 保留 6 位小数即可满足精度，避免超长浮点串
    return String.format(java.util.Locale.US, "%.6f", v);
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}

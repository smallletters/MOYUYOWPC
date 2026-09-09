package com.moyuyo.api.controller;

import com.moyuyo.common.Result;
import com.moyuyo.common.dto.geo.GeoPlaceVO;
import com.moyuyo.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@Tag(name = "地理位置代理（海外 Google）")
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {

  private final GeoService geoService;

  @Operation(summary = "按关键词搜索附近地点（Google Nearby Search 代理）")
  @GetMapping("/places")
  public Result<java.util.List<GeoPlaceVO>> places(
      @RequestParam(required = false) String keyword,
      @RequestParam double lat,
      @RequestParam double lng,
      @RequestParam(defaultValue = "5000") int radius) {
    // 入参边界校验，避免把异常/超长请求转发给上游
    if (Double.isNaN(lat) || lat < -90 || lat > 90) {
      return Result.error(400, "纬度参数非法");
    }
    if (Double.isNaN(lng) || lng < -180 || lng > 180) {
      return Result.error(400, "经度参数非法");
    }
    if (radius < 1 || radius > 50000) {
      return Result.error(400, "搜索半径需在 1~50000 米之间");
    }
    if (keyword != null && keyword.length() > 100) {
      return Result.error(400, "搜索关键词过长");
    }
    // 无关键词时返回空（前端提示输入），避免空查询请求 Google
    if (keyword == null || keyword.trim().isEmpty()) {
      return Result.success(Collections.emptyList());
    }
    return Result.success(geoService.searchPlaces(keyword.trim(), lat, lng, radius));
  }

  @Operation(summary = "坐标反查地址（Google Geocoding 代理）")
  @GetMapping("/reverse")
  public Result<Map<String, String>> reverse(
      @RequestParam double lat,
      @RequestParam double lng) {
    if (Double.isNaN(lat) || lat < -90 || lat > 90 || Double.isNaN(lng) || lng < -180 || lng > 180) {
      return Result.error(400, "经纬度参数非法");
    }
    return Result.success(Map.of("address", geoService.reverseGeocode(lat, lng)));
  }
}

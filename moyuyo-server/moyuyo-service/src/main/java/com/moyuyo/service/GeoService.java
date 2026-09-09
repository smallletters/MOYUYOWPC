package com.moyuyo.service;

import com.moyuyo.common.dto.geo.GeoPlaceVO;

import java.util.List;

/**
 * 地理位置代理（海外 Google Places / Geocoding）
 * 说明：API Key 只存服务端环境变量，前端通过本服务查询，避免密钥泄漏与跨域问题。
 */
public interface GeoService {

  /**
   * 按关键词搜索附近地点（Google Nearby Search 代理）
   *
   * @param keyword 关键词（可空：返回当前位置附近热门地点）
   * @param lat 纬度（WGS84）
   * @param lng 经度（WGS84）
   * @param radius 搜索半径（米，1~50000）
   */
  List<GeoPlaceVO> searchPlaces(String keyword, double lat, double lng, int radius);

  /**
   * 坐标反查地址（Google Geocoding 代理）
   *
   * @return 格式化地址文本
   */
  String reverseGeocode(double lat, double lng);
}

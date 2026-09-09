package com.moyuyo.common.dto.geo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Google Places 附近地点结果（后端代理层统一出的轻量结构）
 */
@Data
@Schema(description = "附近地点")
public class GeoPlaceVO {

  private String name;

  private String address;

  private Double lat;

  private Double lng;
}

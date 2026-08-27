package com.moyuyo.common.dto.admin.userprofile;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户访问过的商品列表项（来自 mo_browsing_history 聚合 + mo_product 关联）
 */
@Data
public class UserVisitedProductResponse {

  /** 浏览记录主键 */
  private Long id;

  /** 商品 ID */
  private Long productId;

  /** 商品名称 */
  private String productName;

  /** 商品主图 URL */
  private String mainImage;

  /** 商品价格（前端按需除以 100） */
  private Long price;

  /** 该商品被该用户访问的累计次数（去重后的浏览次数） */
  private Integer viewCount;

  /** 最近访问时间 */
  private LocalDateTime lastVisitTime;
}
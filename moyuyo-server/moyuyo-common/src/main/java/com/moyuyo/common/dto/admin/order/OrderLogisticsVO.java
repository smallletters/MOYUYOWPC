package com.moyuyo.common.dto.admin.order;

import com.moyuyo.common.dto.logistics.LogisticsVO;
import lombok.Data;

import java.util.List;

/**
 * 管理后台订单物流详情响应（"物流"弹窗使用）
 * 包含：订单物流基础信息（承运商 / 运单号 / 发货时间 / 收货时间 / 当前状态）与轨迹列表
 */
@Data
public class OrderLogisticsVO {

  /** 订单ID */
  private Long orderId;

  /** 订单号 */
  private String orderNo;

  /** 订单当前状态：PENDING_SHIP / SHIPPED / DELIVERED 等 */
  private String orderStatus;

  /** 承运商名称 */
  private String carrier;

  /** 运单号 */
  private String trackingNumber;

  /** 发货时间（ISO 字符串，便于前端格式化） */
  private String shippedAt;

  /** 收货时间 */
  private String receivedAt;

  /** 当前物流状态：NO_RECORD / PENDING / IN_TRANSIT / DELIVERED */
  private String currentStatus;

  /** 当前物流状态中文标签 */
  private String currentStatusLabel;

  /** 物流轨迹（倒序/正序由后端统一为最新在顶部） */
  private List<LogisticsVO.TraceItem> traces;
}

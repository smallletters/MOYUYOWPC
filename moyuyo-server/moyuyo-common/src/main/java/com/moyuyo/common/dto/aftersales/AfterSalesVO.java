package com.moyuyo.common.dto.aftersales;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后 VO（APP 列表/详情、管理后台列表/详情共用）
 */
@Data
@Schema(description = "售后详情")
public class AfterSalesVO {

  private Long id;

  /** 售后单号 */
  private String afterSaleNo;

  private Long orderId;

  /** 订单号（展示用，避免前端只看到订单主键） */
  private String orderNo;

  private Long orderItemId;

  private Long skuId;

  private Long productId;

  private String productName;

  private String skuSpec;

  private String mainImage;

  private Integer quantity;

  /** RETURN_REFUND / REFUND_ONLY / EXCHANGE */
  private String type;

  /** 类型中文标签 */
  private String typeLabel;

  /** RETURN_SHIP / NO_RETURN */
  private String deliveryType;

  private String reason;

  /** 申请退款金额 */
  private BigDecimal amount;

  private String description;

  private String images;

  /** 状态机取值 */
  private String status;

  /** 状态中文标签 */
  private String statusLabel;

  private String returnCompany;

  private String returnTrackingNo;

  private LocalDateTime returnTime;

  private LocalDateTime receiveTime;

  private Long refundId;

  private String refundNo;

  private String rejectReason;

  private LocalDateTime rejectTime;

  private LocalDateTime completeTime;

  private String remark;

  private LocalDateTime createTime;

  /**
   * 进度序号（APP 进度条使用）：0 提交申请 / 1 审核通过 / 2 已寄回 / 3 商家收货 / 4 完成
   * 拒绝、取消返回 -1，由前端展示终止态。
   */
  private Integer progressIndex;

  /** 进度节点数量（APP 按类型渲染：仅退款 3 步，退货退款/换货 5 步） */
  private Integer progressTotal;

  /** 退货收件地址提示（审核通过后由后端下发，APP 展示寄回指引） */
  private List<String> returnInstructions;
}

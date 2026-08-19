package com.moyuyo.common.dto.exchange;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Schema(description = "换货单VO")
public class ExchangeVO {

    private Long id;
    private Long orderId;
    private String exchangeNo;
    private Long oldSkuId;
    private Integer oldQuantity;
    private Long newSkuId;
    private Integer newQuantity;
    private String reason;
    private String description;
    private String images;
    /** 状态：APPLIED/APPROVED/SHIPPED_BACK/RESHIPPED/COMPLETED/CANCELLED */
    private String status;
    private String carrier;
    private String trackingNo;
    private String reshipCarrier;
    private String reshipTracking;
    private LocalDateTime applyTime;
    private LocalDateTime approveTime;
    private LocalDateTime completeTime;
    private LocalDateTime cancelTime;
}

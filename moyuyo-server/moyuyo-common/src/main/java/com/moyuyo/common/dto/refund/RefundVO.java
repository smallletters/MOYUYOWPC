package com.moyuyo.common.dto.refund;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "退款VO")
public class RefundVO {

    private Long id;
    private Long orderId;
    /** 关联订单号（管理后台列表/详情展示用，避免前端只看到订单主键） */
    private String orderNo;
    private String refundNo;
    private String type;
    private BigDecimal amount;
    private String reason;
    private String description;
    private String images;
    /** 拆单退款明细（JSON 字符串，前端按需解析） */
    private String items;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;
    /** 退款商品明细（列表展示图片/标题/SKU；拆单退款优先，整单退款回退到订单商品） */
    private List<RefundItemVO> productItems;
}

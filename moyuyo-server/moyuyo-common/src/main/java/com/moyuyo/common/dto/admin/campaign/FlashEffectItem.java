package com.moyuyo.common.dto.admin.campaign;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 单场秒杀效果明细
 */
@Data
public class FlashEffectItem {

  /** 秒杀 ID */
  private Long id;

  /** 秒杀名称 */
  private String name;

  /** 售罄率（%），售罄为 100 */
  private int selloutRate;

  /** 状态：已售罄 / 进行中 / 已结束 */
  private String status;

  /** 明文摘要（如 "售罄 48s / 500件"） */
  private String detail;

  /** 秒杀 GMV */
  private BigDecimal gmv;
}

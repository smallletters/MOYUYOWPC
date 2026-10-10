package com.moyuyo.common.dto.admin.order;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 管理后台创建订单导出任务请求
 */
@Data
public class OrderExportCreateRequest {

  /** 任务名称,空则由服务端默认为"订单导出" */
  private String taskName;

  /** 订单范围描述,空则由服务端默认为"全部订单" */
  private String orderScope;

  /** 导出格式,空则由服务端默认为"Excel" */
  private String format;

  /**
   * 自定义开始日期（orderScope=自定义 时必填，格式 yyyy-MM-dd，包含）
   * 与前端 el-date-picker type="daterange" v-model 绑定的 [0] 对齐
   */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate startDate;

  /**
   * 自定义结束日期（orderScope=自定义 时必填，格式 yyyy-MM-dd，包含）
   * 与前端 el-date-picker type="daterange" v-model 绑定的 [1] 对齐
   */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate endDate;
}
package com.moyuyo.common.dto.pet;

import lombok.Data;

import java.time.LocalDate;

/**
 * 宠物护理聚合摘要（主页洗护/疫苗/驱虫卡片的数据源）
 * 由"最近一次成长记录" + "护理提醒配置"两部分拼装而成，
 * 前端拿回后即可直接渲染卡片，无需再分别请求 records 与 reminders。
 */
@Data
public class PetCareSummaryVO {

  /** 护理类型：BATH 洗护 / VACCINE 疫苗 / DEWORM 驱虫 / EXAM 体检 */
  private String careType;

  /** 是否已有该类型的历史记录 */
  private Boolean hasRecord;

  /** 该类型历史记录总数 */
  private Integer recordCount;

  /** 最近一次护理发生日期（无记录时为 null） */
  private LocalDate lastRecordDate;

  /** 距最近一次护理的天数（无记录时为 null） */
  private Integer daysAgo;

  /** 提醒是否启用；null 表示尚未配置该类型提醒 */
  private Boolean enabled;

  /** 下次护理日期（提醒未配置时为 null） */
  private LocalDate nextDate;

  /** 护理周期天数（提醒未配置时为 null） */
  private Integer intervalDays;

  /** 提前 N 天提醒（提醒未配置时为 null） */
  private Integer advanceDays;

  /** 上次已推送日期 */
  private LocalDate lastNotifiedDate;

  /** 扩展信息（vaccineType/dewormType 等） */
  private String extra;

  /** 是否已过期（nextDate 早于今天） */
  private Boolean overdue;

  /** 距下次护理天数，负数表示已过期天数；nextDate 为空时为 null */
  private Integer daysUntilNext;
}

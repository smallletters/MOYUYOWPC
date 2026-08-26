package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("mo_service_booking")
public class ServiceBookingEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private Long petId;

  /** BATH / GROOMING / VET / HOTEL / TRAIN */
  private String serviceType;

  private Long staffId;

  private LocalDate bookingDate;

  private LocalTime bookingTime;

  private Integer durationMin;

  private String address;

  private String contactPhone;

  private String notes;

  private BigDecimal price;

  /** PENDING / CONFIRMED / COMPLETED / CANCELLED */
  private String status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
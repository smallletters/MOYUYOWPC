package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mo_growth_record")
public class GrowthRecordEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long petId;

  private Long userId;

  private String recordType;

  private String content;

  private String mediaUrl;

  private LocalDate recordDate;

  // 表列名为 create_time/update_time（非 created_at/updated_at），显式映射避免 SQL 报错
  @TableField(value = "create_time", fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
}

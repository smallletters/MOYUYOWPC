package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "mo_pet", autoResultMap = true)
public class PetEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long userId;

  private String name;

  /** 分类：DOG / CAT / OTHER（与 mo_pet.type 列对齐，供插入 SQL 使用） */
  private String type;

  /** 中文显示值（前端 picker 直接使用的狗狗/猫咪/...） */
  private String species;

  private String breed;

  private String gender;

  /** 宠物状态：ACTIVE(正常)/DIED(去世)/ACCIDENT(意外)。默认 ACTIVE */
  private String status;

  private LocalDate birthday;

  /** 加入家庭时间（档案页展示/记录） */
  private LocalDate adoptedAt;

  private String avatar;

  private Double weight;

  private String notes;

  /** 性格标签：mo_pet.tags 为 JSON 数组列（如 ["粘人","活泼"]），用 JacksonTypeHandler 读写 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private List<String> tags;

  @TableLogic
  private Integer deleted;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;
}

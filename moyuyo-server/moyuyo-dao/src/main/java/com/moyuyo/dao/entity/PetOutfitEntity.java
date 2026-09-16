package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mo_pet_outfit")
public class PetOutfitEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long petId;

  /**
   * 装扮归属用户：
   * - NULL = 系统默认装扮(运营 seed 数据,所有用户可见)
   * - 非 NULL = 用户上传的自定义形象(仅 owner 可见/可删)
   * <p>
   * V20260916_02 新增。原表无此字段,任何用户都能装备/删除他人装扮,
   * 现 Service 层所有写操作必须校验 userId 防止越权。
   */
  private Long userId;

  private String category;

  private String name;

  private String imageUrl;

  private Boolean owned;

  private Boolean equipped;

  private BigDecimal price;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}

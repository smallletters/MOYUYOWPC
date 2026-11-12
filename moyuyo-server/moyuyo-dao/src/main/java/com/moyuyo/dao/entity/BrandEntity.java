package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌表 mo_brand：商品表 brand_id 引用本表主键
 */
@Data
@TableName("mo_brand")
public class BrandEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String logo;

    private String description;

    private LocalDateTime createTime;
}

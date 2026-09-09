package com.moyuyo.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 会员卡号自增序列表（mo_member_no_seq）。
 * 仅作号码分配器：INSERT 一条即取自增 id，避免引入额外计数表/分布式锁。
 */
@Data
@TableName("mo_member_no_seq")
public class MemberNoSeqEntity {

  @TableId(type = IdType.AUTO)
  private Long id;
}

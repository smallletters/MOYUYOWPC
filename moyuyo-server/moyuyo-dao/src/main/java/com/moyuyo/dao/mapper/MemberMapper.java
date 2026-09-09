package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.MemberEntity;
import com.moyuyo.dao.entity.MemberNoSeqEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface MemberMapper extends BaseMapper<MemberEntity> {

  /**
   * 分配下一个会员卡号：向序列表插入一行并回填自增 id。
   * 该 id 递增、全局唯一，卡号即 %012d(id)。
   */
  @Insert("INSERT INTO mo_member_no_seq (id) VALUES (NULL)")
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertSeq(MemberNoSeqEntity seq);
}

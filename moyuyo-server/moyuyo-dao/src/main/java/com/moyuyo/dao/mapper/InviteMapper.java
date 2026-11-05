package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.dto.InviteMonthlyRankRow;
import com.moyuyo.dao.entity.InviteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InviteMapper extends BaseMapper<InviteEntity> {

  /**
   * 统计区间内邀请人数严格多于指定值的用户数，用于计算当前用户的月度排名（并列同名次）。
   */
  @Select("SELECT COUNT(*) FROM ("
      + "SELECT user_id, COUNT(*) AS cnt FROM mo_invite "
      + "WHERE invited_user_id IS NOT NULL AND create_time >= #{start} AND create_time < #{end} "
      + "GROUP BY user_id"
      + ") t WHERE t.cnt > #{myCount}")
  long countUsersAhead(@Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end,
                       @Param("myCount") long myCount);

  /**
   * 统计区间内邀请人数 TOP N：人数降序，同人数按最早一条邀请记录优先。
   */
  @Select("SELECT user_id AS userId, COUNT(*) AS inviteCount FROM mo_invite "
      + "WHERE invited_user_id IS NOT NULL AND create_time >= #{start} AND create_time < #{end} "
      + "GROUP BY user_id ORDER BY inviteCount DESC, MIN(id) ASC LIMIT #{limit}")
  List<InviteMonthlyRankRow> selectMonthlyTopInviters(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      @Param("limit") int limit);
}

package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.CsMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 客服会话消息 Mapper
 */
@Mapper
public interface CsMessageMapper extends BaseMapper<CsMessageEntity> {

  /**
   * 批量统计各会话的消息条数（会话列表页避免逐条 COUNT）
   *
   * @param sessionIds 会话主键集合，调用方需保证非空
   * @return 每行包含 sessionId / cnt
   */
  @Select("<script>" +
      "SELECT session_id AS sessionId, COUNT(*) AS cnt FROM mo_cs_message " +
      "WHERE session_id IN " +
      "<foreach collection='sessionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
      "GROUP BY session_id" +
      "</script>")
  List<Map<String, Object>> countBySessionIds(@Param("sessionIds") List<Long> sessionIds);

  /**
   * 批量取各会话最后一条消息的正文（MySQL 8 窗口函数，按 create_time、id 倒序取第一条）
   *
   * @param sessionIds 会话主键集合，调用方需保证非空
   * @return 每行包含 sessionId / content
   */
  @Select("<script>" +
      "SELECT sessionId, content FROM (" +
      "SELECT session_id AS sessionId, content, " +
      "ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY create_time DESC, id DESC) AS rn " +
      "FROM mo_cs_message WHERE session_id IN " +
      "<foreach collection='sessionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
      ") t WHERE t.rn = 1" +
      "</script>")
  List<Map<String, Object>> selectLatestContentBySessionIds(@Param("sessionIds") List<Long> sessionIds);
}

package com.moyuyo.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.entity.ProductReviewEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReviewEntity> {

    /**
     * 按状态分组统计指定时间区间内的评价数量。
     * 返回 List<Map>，每行形如 {status: "PENDING", cnt: 5}。
     * 缺失的状态不会出现在结果中，调用方需自行兜底。
     */
    @Select("SELECT `status` AS status, COUNT(*) AS cnt " +
            "FROM mo_product_review " +
            "WHERE create_time >= #{start} AND create_time < #{end} " +
            "GROUP BY `status`")
    List<Map<String, Object>> countByStatusInTimeRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}

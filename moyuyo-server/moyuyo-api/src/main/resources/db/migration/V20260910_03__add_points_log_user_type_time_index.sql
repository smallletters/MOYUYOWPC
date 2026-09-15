-- 积分流水表补充复合索引
-- 场景：签到/断签判定改为按 (user_id, type, created_at) 过滤（见 MemberServiceImpl.getRecentCheckinDates），
--      任务统计也按 (user_id, change_value, created_at) 过滤。
--      现有 idx_user_id / idx_points_log_created_at 均为单列索引，命中 user_id 后仍需回表逐行过滤 type 与时间。
-- 该复合索引让"某用户某类型 + 时间范围"的查询走索引范围扫描。
ALTER TABLE mo_points_log ADD INDEX idx_points_log_user_type_time (user_id, type, created_at);

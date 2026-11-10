-- 用户表补充 last_login_time 索引
-- 场景：管理后台「今日访客 / 今日活跃 / 昨日活跃」按 last_login_time 做时间范围 COUNT
--      （AdminCrmController.realtime、AdminDashboardServiceImpl、AdminUserManageServiceImpl.getStats），
--      原表只有 uk_email / idx_user_country / idx_user_delete / idx_phone，该字段无索引 → 全表扫描。
-- 注意：在线人数已改为 Redis 活跃心跳口径（auth:active:users），不再依赖该字段；
--      本索引只服务"每日活跃/访客"这类按天统计的查询。
ALTER TABLE mo_user ADD INDEX idx_user_last_login_time (last_login_time);

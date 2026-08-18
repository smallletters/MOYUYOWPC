-- ============================================================
-- V20260807_01__user_coupon_unique_index.sql
-- 为 mo_user_coupon 增加 (user_id, coupon_id) 唯一索引
--
-- 业务背景：
--   原 CouponServiceImpl.claimCoupon 走"先 selectCount 再 insert"的非原子路径，
--   并发场景下同一用户对同一券种可能插入多条记录（超发）。
--   现已重构为先 insert + 依赖唯一索引兜底：DuplicateKeyException 直接转为
--   "已领取过该优惠券"语义，依赖数据库唯一约束彻底杜绝竞态。
--
-- 索引选择：
--   - UNIQUE (user_id, coupon_id)：业务上同一用户对同一券种只能领一次
--   - 索引顺序：user_id 在前便于"我的优惠券"列表查询命中前缀
--   - 与已有的 idx_user_coupon_status_time (user_id, status, create_time) 不冲突
--
-- 注意：
--   - 部署前若历史数据已存在重复记录，需先清理后再执行 ADD UNIQUE
--   - 提供 DELETE 子查询兜底清理（仅保留最早一条），生产环境 DBA 可按需手工执行
-- ============================================================

-- 1) 兜底清理历史重复数据（保留 receive_time 最早的一条）
DELETE u1 FROM `mo_user_coupon` u1
INNER JOIN `mo_user_coupon` u2
  ON u1.user_id = u2.user_id
  AND u1.coupon_id = u2.coupon_id
  AND u1.id > u2.id
  AND u1.receive_time > u2.receive_time;

-- 2) 添加唯一索引（防超发，作为 CouponServiceImpl.claimCoupon 的并发兜底）
ALTER TABLE `mo_user_coupon` ADD UNIQUE INDEX IF NOT EXISTS `uk_user_coupon` (`user_id`, `coupon_id`);
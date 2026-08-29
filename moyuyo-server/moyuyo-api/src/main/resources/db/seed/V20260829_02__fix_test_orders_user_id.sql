-- ============================================================
-- V20260829_02__fix_test_orders_user_id.sql
-- 修复测试订单的 user_id 关联
-- ============================================================
-- 问题背景：
--   V20260829_01__test_orders_seed.sql 预设 user_id=180000099
--   但 test@moyuyo.com 实际通过 APP 注册的 user_id 不同（如 200000001）
--   导致 APP 端登录后看不到测试订单
--
-- 修复方案：
--   通过 email 子查询动态获取 test@moyuyo.com 的实际 user_id
--   将所有 user_id=180000099 的测试订单、地址更新为正确 user_id
--
-- 幂等性：
--   UPDATE 语句可重复执行，user_id=180000099 不存在时影响 0 行
--   修复后再次启动不会报错
-- ============================================================

-- 1. 修复测试订单的 user_id
UPDATE mo_order
SET user_id = (SELECT id FROM (SELECT id FROM mo_user WHERE email = 'test@moyuyo.com') AS tmp)
WHERE user_id = 180000099;

-- 2. 修复测试地址的 user_id
UPDATE mo_address
SET user_id = (SELECT id FROM (SELECT id FROM mo_user WHERE email = 'test@moyuyo.com') AS tmp)
WHERE user_id = 180000099;

-- ============================================================
-- V20261016_01__add_user_points_frozen.sql
-- 积分「冻结(占用)」语义支持。
--
-- 背景：原实现下单时只把抵扣金额减进 pay_amount，mo_user.points 直到支付成功回调才扣减，
--      导致同一批积分可被多笔待付款订单同时抵扣（每笔都减钱，只有第一笔能扣成功，其余走人工对账）。
-- 目标：下单即冻结 -> 支付成功转实扣 -> 取消/超时解冻回退；可用积分 = points - points_frozen。
--
-- 本脚本做两件事：
--   1. mo_user 新增 points_frozen 列（INT NOT NULL DEFAULT 0），记录被未支付订单占用的积分。
--   2. 存量历史订单（points_used>0 且仍为 PENDING_PAY）在迁移瞬间并未冻结任何积分，
--      为避免把存量待付款订单的抵扣积分一并视为已冻结，这里保持默认 0，不做回填：
--      解冻路径用 points_frozen >= ? 做条件更新，存量订单取消时因无冻结而安全跳过（幂等、不误还）。
--
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA 守卫保证脚本可重复执行。
--
-- [版本号变更说明]
--   本迁移初版命名为 V20260929_01，与既有 V20260929_01__add_carrier_api_config.sql 撞号，
--   Flyway 报 "Found more than one migration with version 20260929.01"，Spring 容器
--   flywayInitializer 初始化失败 -> 整体启动失败。且本环境 Flyway 已应用的最大版本为 20261011.01
--   （历史表名为 schema_history，非 flyway_schema_history）；任何小于等于 20261011.01 的新版本号
--   都会命中 outOfOrder=false 的 "resolved migration not applied and below latest applied" 校验而
--   启动失败。故改名为 V20261016_01（构建/上线日 2026-10-16，该版本号此前未被占用）。内容与初版一致。
--
-- [注释语法注意事项]
--   MySQL 仅当双横线后紧跟空白字符（空格/Tab）时才把该行视为注释；双横线紧邻非空白字符
--   （例如双横线后直接跟全角括号）的写法会被 MySQL 当作算术表达式解析并报 1064 语法错误，
--   本迁移首次执行即因此失败。故本文件所有注释行一律写成「双横线 + 一个空格」前缀。
-- ============================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mo_user'
      AND COLUMN_NAME = 'points_frozen');

SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `mo_user` ADD COLUMN `points_frozen` INT NOT NULL DEFAULT 0 COMMENT ''因未支付订单被冻结(占用)的积分；可用积分=points-points_frozen'' AFTER `points`',
    'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

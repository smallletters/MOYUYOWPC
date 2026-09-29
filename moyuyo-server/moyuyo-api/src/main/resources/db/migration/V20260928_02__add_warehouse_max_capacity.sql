-- ============================================================
-- V20260928_02__add_warehouse_max_capacity.sql
-- 为 mo_warehouse 增加 max_capacity_qty 字段（最大可容纳库存件数）。
-- 用途：替换 usage_rate 字段作为仓库利用率的真实计算口径。
-- 计算公式：仓库利用率 = ∑ mo_inventory.quantity / max_capacity_qty
-- 说明：MySQL 不支持 ADD COLUMN IF NOT EXISTS；改用动态 SQL 实现列存在性检查，
--       Flyway 默认按 ;切分语句，用 SET + PREPARE + EXECUTE 可避免 DELIMITER。
-- ============================================================

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mo_warehouse'
      AND COLUMN_NAME = 'max_capacity_qty'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `mo_warehouse` ADD COLUMN `max_capacity_qty` INT NOT NULL DEFAULT 0 COMMENT ''最大可容纳库存件数'' AFTER `total_stock`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为所有现存仓库设置初始容量（按仓库等级分层）
-- 容量选择策略：
--   1) 海外仓 (OVERSEAS)：2000-5000 件
--   2) 自营主仓 (SELF/ACTIVE)：1000 件
--   3) 自营次仓 (SELF/INACTIVE)：1000 件（待重启用）
--   4) 第三方仓 (THIRD_PARTY)：5000 件（参考 seed: 武汉第三方仓 area=1200）
UPDATE `mo_warehouse` SET `max_capacity_qty` = 1000  WHERE `id` = 194000001; -- 上海自营仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 1000  WHERE `id` = 194000002; -- 广州自营仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 1000  WHERE `id` = 194000003; -- 成都自营仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 5000  WHERE `id` = 194000004; -- 武汉第三方仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 1000  WHERE `id` = 194000005; -- 天津自营仓(INACTIVE)
UPDATE `mo_warehouse` SET `max_capacity_qty` = 500   WHERE `id` = 194000006; -- 洛杉矶海外仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 2000  WHERE `id` = 194000007; -- 汉堡海外仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 1000  WHERE `id` = 194000008; -- 东京海外仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 5000  WHERE `id` = 194000009; -- 伦敦海外仓
UPDATE `mo_warehouse` SET `max_capacity_qty` = 3000  WHERE `id` = 194000010; -- 悉尼海外仓(MAINTENANCE)

-- 兜底：若未来新增仓库忘记设置，给予默认 1000
UPDATE `mo_warehouse` SET `max_capacity_qty` = 1000 WHERE `max_capacity_qty` = 0;
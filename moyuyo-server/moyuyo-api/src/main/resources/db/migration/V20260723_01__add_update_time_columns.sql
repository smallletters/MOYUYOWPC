-- 为缺少 update_time 字段的 admin 表补充该列
ALTER TABLE `mo_admin_user` ADD COLUMN `update_time` DATETIME AFTER `create_time`;
ALTER TABLE `mo_cs_session` ADD COLUMN `update_time` DATETIME AFTER `create_time`;
ALTER TABLE `mo_ab_test` ADD COLUMN `update_time` DATETIME AFTER `create_time`;
ALTER TABLE `mo_inventory_transfer` ADD COLUMN `update_time` DATETIME AFTER `create_time`;

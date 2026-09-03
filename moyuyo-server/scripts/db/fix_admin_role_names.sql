-- 修复 docker MySQL 重建后 mo_admin_role 因客户端字符集不一致导致的 "?????" 乱码
-- 按 code 匹配，覆盖 name / description
-- 注意：客户端必须以 --default-character-set=utf8mb4 执行，否则写入又会变成 '?'

UPDATE `mo_admin_role` SET `name` = '超级管理员', `description` = '拥有所有权限'    WHERE `code` = 'SUPER_ADMIN';
UPDATE `mo_admin_role` SET `name` = '运营管理员', `description` = '订单/商品/用户管理权限' WHERE `code` = 'OPERATOR';
UPDATE `mo_admin_role` SET `name` = '客服人员',   `description` = '客服会话和工单权限' WHERE `code` = 'CUSTOMER_SVC';
UPDATE `mo_admin_role` SET `name` = '财务人员',   `description` = '财务相关权限'   WHERE `code` = 'FINANCE';
UPDATE `mo_admin_role` SET `name` = '数据查看员', `description` = '仅查看数据权限' WHERE `code` = 'VIEWER';

-- 校验修复结果
SELECT id, code, name, HEX(name) AS name_hex, description FROM mo_admin_role ORDER BY id;

-- ============================================================
-- V20260820_01__mark_preset_roles.sql
-- 回填系统预置角色的 is_preset=1，与 AdminRoleServiceImpl.delete 中
-- "预设角色禁止删除"的业务校验对齐。
--
-- 之所以需要本补丁：
--   - V20260720_02 默认角色 INSERT 未包含 is_preset 字段（彼时该列尚未添加）
--   - V20260727_01 才给 mo_admin_role 添加了 is_preset 列，默认 0
--   - 因此 5 个系统角色：超级管理员 / 运营管理员 / 客服人员 / 财务人员 / 数据查看员
--     在 MySQL 中实际是 is_preset=0，与 AdminRoleService / 前端业务语义不一致
-- ============================================================

-- 仅在系统未自动标识为预置时再写，避免对显式修改过预设标记的数据产生副作用
-- 注意：V20260803_01 把 CS_STAFF 重命名为 CUSTOMER_SVC，所以匹配用 name 而非 code 更稳妥
UPDATE `mo_admin_role`
SET `is_preset` = 1,
    `update_time` = NOW()
WHERE `name` IN ('超级管理员', '运营管理员', '客服人员', '财务人员', '数据查看员')
  AND (`is_preset` = 0 OR `is_preset` IS NULL);

-- 同时为种子中的管理员用户绑定对应 role 字符串，便于"查看成员"等接口能够匹配
UPDATE `mo_admin_user` SET `role` = 'SUPER_ADMIN'
WHERE `email` = 'admin@moyuyo.com' AND (`role` IS NULL OR `role` = '');

-- ============================================================
-- V20261109_05__dm_stranger_guard_config.sql
-- 私信陌生人防骚扰默认配置（管理后台「系统配置 → 私信安全」可调整）
--
-- 说明：
--   1. stranger_first_limit：对方未回复前最多发送条数，默认 1，0=不限制
--   2. stranger_daily_limit：未互动会话每天最多发送条数，默认 10，0=不限制
--   3. 复用 mo_system_config，INSERT IGNORE 保证重复执行安全
-- ============================================================

INSERT IGNORE INTO mo_system_config (id, config_key, config_value, remark, create_time, update_time)
VALUES
(207001001, 'dm.guard.stranger_first_limit', '1',
 '陌生人私信首条上限：对方未回复前最多发送条数（0=不限制）', NOW(), NOW()),
(207001002, 'dm.guard.stranger_daily_limit', '10',
 '陌生人私信每日上限：未互动会话每天最多发送条数（0=不限制）', NOW(), NOW());

-- 防骚扰判定按「会话 + 发送方」统计消息数，补充联合索引避免扫会话内全部消息
ALTER TABLE `mo_dm_message`
    ADD INDEX `idx_dm_msg_conv_sender` (`conversation_id`, `sender_id`);

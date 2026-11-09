-- ============================================================
-- V20261109_03__notification_biz_type.sql
-- 通知增加业务类型，供 APP 按业务跳转（订单详情 / 售后详情）
-- 历史数据 biz_type 为空：APP 侧按 type=ORDER + relatedId 回退到订单详情
-- ============================================================

ALTER TABLE `mo_notification`
    ADD COLUMN `biz_type` VARCHAR(20) NULL COMMENT '业务类型 ORDER/AFTER_SALE' AFTER `type`;

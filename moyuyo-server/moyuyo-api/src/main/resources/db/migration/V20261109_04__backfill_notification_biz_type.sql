-- ============================================================
-- V20261109_04__backfill_notification_biz_type.sql
-- 一次性回填历史通知的 biz_type（可重复执行，只处理 biz_type 为空的记录）
--
-- 回填规则：
--   1) type=ORDER 且 related_id 命中 mo_after_sales.id → AFTER_SALE（售后通知）
--   2) type=ORDER 且 related_id 命中 mo_order.id       → ORDER（订单通知）
--   3) 其余（活动/系统/私信等）保持 NULL，APP 走原有消息详情页
--
-- 注意：必须先执行第 1 条再执行第 2 条。
--   两张表的主键都由雪花算法生成，理论存在极小概率的跨表 ID 相同，
--   先回填售后可保证这类歧义记录被判定为售后，点击后进入售后详情。
-- ============================================================

-- 1) 售后通知
UPDATE `mo_notification` n
    JOIN `mo_after_sales` a ON a.`id` = n.`related_id`
SET n.`biz_type` = 'AFTER_SALE'
WHERE n.`biz_type` IS NULL
  AND UPPER(n.`type`) = 'ORDER';

-- 2) 订单通知
UPDATE `mo_notification` n
    JOIN `mo_order` o ON o.`id` = n.`related_id`
SET n.`biz_type` = 'ORDER'
WHERE n.`biz_type` IS NULL
  AND UPPER(n.`type`) = 'ORDER';

-- 3) 校验（只读，可单独执行）：
-- SELECT biz_type, COUNT(*) AS cnt
-- FROM `mo_notification`
-- WHERE UPPER(`type`) = 'ORDER'
-- GROUP BY biz_type;

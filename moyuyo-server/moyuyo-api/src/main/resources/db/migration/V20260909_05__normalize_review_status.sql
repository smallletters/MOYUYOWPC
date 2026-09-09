-- 商品评价状态统一为英文枚举 PENDING / APPROVED / REJECTED
-- 历史脏值映射：
--   待审核 -> PENDING
--   已审核 -> APPROVED（含管理端回复过、状态误置为 REPLIED 的记录，回复仅追加到 content，评价仍需可见）
--   已驳回 -> REJECTED
UPDATE mo_product_review
SET status = CASE status
    WHEN '待审核' THEN 'PENDING'
    WHEN '已审核' THEN 'APPROVED'
    WHEN 'REPLIED' THEN 'APPROVED'
    WHEN '已驳回' THEN 'REJECTED'
    ELSE status
    END
WHERE status IN ('待审核', '已审核', 'REPLIED', '已驳回');

-- 防重复评价：同一用户对同一订单明细只能有一条评价（先查后插 + 唯一索引兜底）
-- 建唯一索引前先去重（历史重复数据）：
-- 1) 若同组存在 APPROVED（含系统默认好评），删除同组其它非 APPROVED 记录；
-- 2) 对仍重复的记录仅保留 id 最小的一行。
DELETE r FROM mo_product_review r
JOIN mo_product_review a
  ON r.user_id = a.user_id AND r.order_item_id = a.order_item_id
 AND r.order_item_id IS NOT NULL
 AND a.status = 'APPROVED'
 AND r.id <> a.id AND r.status <> 'APPROVED';

DELETE r FROM mo_product_review r
JOIN mo_product_review r2
  ON r.user_id = r2.user_id AND r.order_item_id = r2.order_item_id
 AND r.order_item_id IS NOT NULL
 AND r.id > r2.id;

CREATE UNIQUE INDEX uk_user_order_item ON mo_product_review (user_id, order_item_id);

-- 修正 mo_coupon 表中 PERCENT 类型券的 discount_value 语义：
-- 旧值存的是『折数』(9 表示 9 折)，新约定为『价格百分比』(9 折 = 90)。
-- 历史数据折数 ≤ 10 时按 ×10 转成价格百分比；其余数值假定已是价格百分比，不动。
-- 仅修正 type='PERCENT' 的记录。

UPDATE mo_coupon
SET discount_value = discount_value * 10
WHERE type = 'PERCENT'
  AND discount_value IS NOT NULL
  AND discount_value <= 10;

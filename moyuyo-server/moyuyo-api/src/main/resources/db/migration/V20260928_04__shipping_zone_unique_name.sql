-- ============================================================
-- mo_shipping_zone.name 加 UNIQUE 索引
--
-- 目的：
-- 1) 把"区域名唯一"的业务约束从应用层提升到 DB 层，避免并发 create 时 controller 查重漏判；
-- 2) 简化 service 实现：直接抛 DuplicateKeyException 让 GlobalExceptionHandler 统一返回 409；
-- 3) 历史数据：先 INSERT 一份 dedup 视图排查重复，无重复则加索引
-- ============================================================

-- 防御：迁移前先 dedup 检查，若有重名直接失败（避免索引加不上导致迁移断流）
-- 仅在生产部署前人工执行排查：SELECT name, COUNT(*) FROM mo_shipping_zone GROUP BY name HAVING COUNT(*) > 1;
-- 重名记录合并：保留 id 较小的，把同名 id 重复名后追加 ' (副本)' 区分

ALTER TABLE `mo_shipping_zone`
    ADD UNIQUE INDEX `uk_shipping_zone_name` (`name`);
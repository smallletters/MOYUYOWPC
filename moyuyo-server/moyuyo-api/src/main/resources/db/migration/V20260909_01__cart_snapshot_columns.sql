-- ============================================================
-- 修复 C 端购物车：mo_cart 表与 CartEntity 实体不一致
-- 背景：CartEntity 映射了 product_id / productName / mainImage / price 等字段，
--      但 mo_cart 仅有 sku_id/quantity/selected，导致加购 SQL 报
--      Unknown column 'product_id'，接口 500。
-- 方案：为 mo_cart 补齐商品快照列（商品ID / 名称 / 主图 / 加购单价），
--      与 C 端购物车接口返回快照的预期（前端 cart 页渲染依赖）保持一致。
-- 列选择：沿用原表 selected 作为勾选列，不改动既有索引 idx_cart_user_selected。
-- 注意：本迁移仅新增列，幂等约束下执行一次即可，勿手工反复重跑。
-- ============================================================
ALTER TABLE mo_cart
    ADD COLUMN product_id BIGINT NULL COMMENT '商品ID' AFTER sku_id,
    ADD COLUMN product_name VARCHAR(255) NULL COMMENT '商品名称快照' AFTER product_id,
    ADD COLUMN main_image VARCHAR(500) NULL COMMENT '商品主图快照' AFTER product_name,
    ADD COLUMN price DECIMAL(10,2) NULL COMMENT '加购时单价快照' AFTER main_image;

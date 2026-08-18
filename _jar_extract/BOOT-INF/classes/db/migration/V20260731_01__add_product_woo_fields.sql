-- ============================================================
-- V20260731_01__add_product_woo_fields.sql
-- 为 mo_product 增加 WooCommerce 对齐字段：
--   short_description, weight, tags, product_type, manage_stock
-- ============================================================

ALTER TABLE mo_product
  ADD COLUMN short_detail   TEXT            NULL  COMMENT '简短描述 (WooCommerce short_description)',
  ADD COLUMN weight         DECIMAL(10,3)   NULL  COMMENT '商品重量 (WooCommerce weight)',
  ADD COLUMN tags           VARCHAR(512)    NULL  COMMENT '商品标签，逗号分隔 (WooCommerce tags)',
  ADD COLUMN product_type   VARCHAR(16)     NOT NULL DEFAULT 'simple' COMMENT '商品类型 simple/variable (WooCommerce type)',
  ADD COLUMN manage_stock   TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否启用库存管理 (WooCommerce manage_stock)';

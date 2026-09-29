-- ============================================================
-- V3__seed_data.sql
-- 初始化基础数据：分类、品牌、协议版本、CMP 默认同意
-- ============================================================

-- 1. 商品分类（欧美宠物用品）
INSERT INTO mo_category (id, parent_id, name, sort, level) VALUES
  (1,    0, 'Bath & Grooming', 1, 1),   -- 洗护
  (101,  1,   'Shampoo',        1, 2),   -- 沐浴露
  (102,  1,   'Conditioner',    2, 2),   -- 护毛素
  (103,  1,   'Ear Care',       3, 2),   -- 耳部清洁
  (104,  1,   'Dental Care',    4, 2),   -- 口腔护理
  (2,    0, 'Apparel',         2, 1),   -- 服饰
  (201,  2,   'Coats & Sweaters', 1, 2),
  (202,  2,   'T-Shirts',         2, 2),
  (203,  2,   'Costumes',         3, 2),
  (3,    0, 'Beds & Furniture',  3, 1),
  (4,    0, 'Toys',              4, 1),
  (5,    0, 'Feeding',           5, 1),
  (6,    0, 'Travel & Outdoor',  6, 1);

-- 2. 品牌
INSERT INTO mo_brand (id, name) VALUES
  (1, 'MOYUYO'),
  (2, 'PetSafe'),
  (3, 'KONG'),
  (4, 'Hurtta'),
  (5, 'Puppia');

-- 3. GDPR 协议版本
INSERT INTO mo_consent_log (id, user_id, consent_type, granted, version, source) VALUES
  (1, 0, 'PRIVACY_POLICY', 1, '1.0.0', 'SYSTEM_INIT'),
  (2, 0, 'TERMS',          1, '1.0.0', 'SYSTEM_INIT');

-- 4. 同步位点初始化
INSERT INTO mo_sync_checkpoint (id, biz_type, last_sync_time, last_sync_id) VALUES
  ('woo_product', 'PRODUCT', '2020-01-01T00:00:00Z', ''),
  ('woo_order',   'ORDER',   '2020-01-01T00:00:00Z', '');

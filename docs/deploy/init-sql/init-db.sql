-- ============================================================
-- MOYUYO 数据库初始化脚本
-- 执行顺序：V1 → V2 → V3 → V4 → V5 → V6 → V7
-- 使用方法：mysql -u root -p moyuyo_prod < init-db.sql
-- ============================================================

-- --------------------------
-- V1: 用户与 OAuth 初始化
-- --------------------------
source V1__init_user_and_oauth.sql;

-- --------------------------
-- V2: 业务表初始化
-- --------------------------
source V2__init_business_tables.sql;

-- --------------------------
-- V3: 种子数据
-- --------------------------
source V3__seed_data.sql;

-- --------------------------
-- V4: 索引与分区
-- --------------------------
source V4__indexes_and_partition.sql;

-- --------------------------
-- V5: 品牌与 IP 初始化
-- --------------------------
source V5__init_brand_and_ip.sql;

-- --------------------------
-- V6: Pet Hub 初始化
-- --------------------------
source V6__init_pet_hub.sql;

-- --------------------------
-- V7: 额外表初始化
-- --------------------------
source V7__init_extra_tables.sql;

-- --------------------------
-- 初始化完成
-- --------------------------
SELECT 'MOYUYO 数据库初始化完成' AS result;
SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema = 'moyuyo_prod';

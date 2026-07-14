-- ============================================================
-- V5__init_brand_and_ip.sql
-- 品牌系统：4 大产品线（Collection） + 4 大 IP 角色（Brand IP）
-- 来源：[MOYUYO_BrandSystem.md §5/§6]
-- ============================================================

-- 1. 产品线（4 大产品线：Gear / Care / Play / Home & Atelier）
CREATE TABLE mo_collection (
  id          BIGINT       NOT NULL                  COMMENT '雪花 ID',
  code        VARCHAR(32)  NOT NULL                  COMMENT 'GEAR/CARE/PLAY/HOME',
  name_en     VARCHAR(64)  NOT NULL                  COMMENT '英文名：Gear Collection',
  name_zh     VARCHAR(64)  NOT NULL                  COMMENT '中文名：出行装备系列',
  ip_code     VARCHAR(32)  NOT NULL                  COMMENT '关联主推 IP：MILO/LUNA/ATLAS/OLIVE',
  description VARCHAR(512) NULL,
  icon        VARCHAR(255) NULL                      COMMENT '产品线 icon CDN',
  banner      VARCHAR(255) NULL                      COMMENT '产品线 banner 图',
  sort        INT          NOT NULL DEFAULT 0,
  on_shelf    TINYINT(1)   NOT NULL DEFAULT 1,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_ip_code (ip_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '品牌产品线（4 大）';

-- 2. 品牌 IP 角色
CREATE TABLE mo_brand_ip (
  id           BIGINT       NOT NULL                  COMMENT '雪花 ID',
  code         VARCHAR(32)  NOT NULL                  COMMENT 'MILO/LUNA/ATLAS/OLIVE',
  name_en      VARCHAR(64)  NOT NULL                  COMMENT 'MILO / LUNA ...',
  name_zh      VARCHAR(64)  NOT NULL                  COMMENT '探险家 / 策展家 ...',
  species      VARCHAR(32)  NOT NULL                  COMMENT '灵缇犬 / 英短金渐层 / 杜宾犬 / 缅因猫',
  personality  VARCHAR(128) NULL                      COMMENT '性格描述',
  tagline      VARCHAR(255) NULL                      COMMENT '一句话 IP 介绍',
  species_label VARCHAR(16) NOT NULL                  COMMENT 'DOG / CAT',
  portrait_2d  VARCHAR(255) NULL                      COMMENT '2D 立绘 CDN',
  portrait_3d  VARCHAR(255) NULL                      COMMENT '3D 模型 URL（GLB）',
  turnaround   VARCHAR(255) NULL                      COMMENT '4 视角 turnaround 图',
  color_hint   VARCHAR(16)  NULL                      COMMENT '主色提示：#DBC98A',
  sort         INT          NOT NULL DEFAULT 0,
  on_shelf     TINYINT(1)   NOT NULL DEFAULT 1,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '品牌 IP 角色（4 大）';

-- 3. 给 mo_category 加产品线归属字段
ALTER TABLE mo_category
  ADD COLUMN collection_id BIGINT NULL COMMENT '关联 mo_collection.id' AFTER parent_id,
  ADD KEY idx_collection (collection_id);

-- 4. 给 mo_product 加主推 IP 字段
ALTER TABLE mo_product
  ADD COLUMN brand_ip_id BIGINT NULL COMMENT '主推 IP（mo_brand_ip.id）' AFTER brand_id,
  ADD KEY idx_brand_ip (brand_ip_id);

-- 5. 给 mo_pet 加用户偏好 IP
ALTER TABLE mo_pet
  ADD COLUMN preferred_ip_code VARCHAR(32) NULL COMMENT '用户偏好 IP：MILO/LUNA/ATLAS/OLIVE' AFTER breed,
  ADD KEY idx_preferred_ip (preferred_ip_code);

-- 6. 商品-IP 多对多关联（一个商品可关联多个 IP）
CREATE TABLE mo_product_ip (
  product_id BIGINT NOT NULL                COMMENT '商品 ID',
  ip_id      BIGINT NOT NULL                COMMENT 'IP ID',
  weight     INT    NOT NULL DEFAULT 100   COMMENT '推荐权重（越大越靠前）',
  PRIMARY KEY (product_id, ip_id),
  KEY idx_ip_id (ip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '商品与 IP 多对多关联';

-- 7. 初始化 4 大产品线
INSERT INTO mo_collection (id, code, name_en, name_zh, ip_code, description, sort) VALUES
  (1, 'GEAR', 'Gear Collection',     '出行装备系列',     'MILO',  '户外 / 旅行 / 牵引 / 饮水',  10),
  (2, 'CARE', 'Care Collection',     '洗护护理系列',     'LUNA',  '日常护理 / 沐浴 / 口腔',      20),
  (3, 'PLAY', 'Play Collection',     '玩具娱乐系列',     'ATLAS', '益智 / 互动 / 解闷',          30),
  (4, 'HOME', 'Home & Atelier Collection', '家居与时尚系列', 'OLIVE', '家居 / 服饰 / 生活方式',  40);

-- 8. 初始化 4 大 IP
INSERT INTO mo_brand_ip (id, code, name_en, name_zh, species, personality, tagline, species_label, color_hint, sort) VALUES
  (1, 'MILO',  'MILO',  '探险家', '灵缇犬',     '自由 / 好奇',   '带宠看更大的世界',   'DOG', '#B38A5A', 10),
  (2, 'LUNA',  'LUNA',  '策展家', '英短金渐层', '优雅 / 精致',   '洗护也是一种仪式感', 'CAT', '#DBC98A', 20),
  (3, 'ATLAS', 'ATLAS', '守护者', '杜宾犬',     '勇敢 / 守护',   '陪伴是最长的告白',   'DOG', '#2E2B29', 30),
  (4, 'OLIVE', 'OLIVE', '艺术家', '缅因猫',     '文艺 / 慵懒',   '生活要有自己的美学', 'CAT', '#ABB9AD', 40);

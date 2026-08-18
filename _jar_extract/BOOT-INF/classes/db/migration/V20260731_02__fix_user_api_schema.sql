-- ============================================================
-- V20260731_02__fix_user_api_schema.sql
-- 修复用户端 API 500 错误：实体字段与数据表结构不一致
-- 1) mo_address        增加 deleted/created_at/updated_at
-- 2) mo_community_post 增加 likes/comments/deleted/update_time
-- 3) mo_user_coupon    增加 used_time/used_order_id
-- 4) mo_gift_card      增加 pin
-- 5) mo_wallet         建表（缺失）
-- 6) mo_pet            增加 species/weight/notes/deleted/created_at/updated_at
-- 7) mo_subscribe_plan 增加 update_time
-- 8) mo_user_subscription 增加 update_time
-- 注意：mo_notification.read 为 MariaDB 保留字，实体层以反引号处理（见代码修复）
-- ============================================================

-- 1) mo_address：实体映射 deleted/created_at/updated_at（@TableLogic + 自动填充）
ALTER TABLE mo_address
  ADD COLUMN IF NOT EXISTS deleted     TINYINT(1)  NOT NULL DEFAULT 0  COMMENT '逻辑删除 0-正常 1-已删',
  ADD COLUMN IF NOT EXISTS created_at  DATETIME    NULL COMMENT '创建时间',
  ADD COLUMN IF NOT EXISTS updated_at  DATETIME    NULL COMMENT '更新时间';

-- 2) mo_community_post：实体映射 likes/comments/deleted/update_time
ALTER TABLE mo_community_post
  ADD COLUMN IF NOT EXISTS likes      INT          NOT NULL DEFAULT 0  COMMENT '点赞数',
  ADD COLUMN IF NOT EXISTS comments   INT          NOT NULL DEFAULT 0  COMMENT '评论数',
  ADD COLUMN IF NOT EXISTS deleted    TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '逻辑删除 0-正常 1-已删',
  ADD COLUMN IF NOT EXISTS update_time DATETIME    NULL COMMENT '更新时间';

-- 3) mo_user_coupon：实体映射 used_time/used_order_id，且缺 create_time
ALTER TABLE mo_user_coupon
  ADD COLUMN IF NOT EXISTS used_time     DATETIME  NULL COMMENT '使用时间',
  ADD COLUMN IF NOT EXISTS used_order_id BIGINT    NULL COMMENT '使用订单ID',
  ADD COLUMN IF NOT EXISTS create_time   DATETIME  NULL COMMENT '创建时间';

-- 4) mo_gift_card：实体映射 pin
ALTER TABLE mo_gift_card
  ADD COLUMN IF NOT EXISTS pin VARCHAR(64) NULL COMMENT '卡密';

-- 5) mo_wallet：缺失建表（与 WalletEntity 对齐）
CREATE TABLE IF NOT EXISTS mo_wallet (
  id               BIGINT       NOT NULL COMMENT '主键',
  user_id          BIGINT       NOT NULL COMMENT '用户ID',
  balance          DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
  total_recharged  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计充值',
  total_spent      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费',
  status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/FROZEN/CLOSED',
  deleted          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-已删',
  created_at       DATETIME     NULL COMMENT '创建时间',
  updated_at       DATETIME     NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_wallet_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户钱包';

-- 6) mo_pet：实体映射 species/weight/notes/deleted/created_at/updated_at
ALTER TABLE mo_pet
  ADD COLUMN IF NOT EXISTS species     VARCHAR(32)  NULL COMMENT '物种',
  ADD COLUMN IF NOT EXISTS weight      DECIMAL(5,2) NULL COMMENT '体重(kg)',
  ADD COLUMN IF NOT EXISTS notes       VARCHAR(512) NULL COMMENT '备注',
  ADD COLUMN IF NOT EXISTS deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-已删',
  ADD COLUMN IF NOT EXISTS created_at  DATETIME     NULL COMMENT '创建时间',
  ADD COLUMN IF NOT EXISTS updated_at  DATETIME     NULL COMMENT '更新时间';

-- 7) mo_subscribe_plan：实体映射 update_time
ALTER TABLE mo_subscribe_plan
  ADD COLUMN IF NOT EXISTS update_time DATETIME NULL COMMENT '更新时间';

-- 8) mo_user_subscription：实体映射 update_time
ALTER TABLE mo_user_subscription
  ADD COLUMN IF NOT EXISTS update_time DATETIME NULL COMMENT '更新时间';

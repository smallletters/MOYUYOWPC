-- 积分商城礼品表（章节 3.2 积分商城兑换）
CREATE TABLE IF NOT EXISTS mo_points_goods (
  id            BIGINT       NOT NULL PRIMARY KEY,
  name          VARCHAR(128) NOT NULL                COMMENT '礼品名称',
  description   VARCHAR(512) NULL                    COMMENT '礼品描述',
  image         VARCHAR(255) NULL                    COMMENT '礼品图片',
  category      VARCHAR(32)  NULL                    COMMENT '分类：DIGITAL/COUPON/PHYSICAL/COUPON_FREESHIP',
  points        INT          NOT NULL DEFAULT 0      COMMENT '兑换所需积分',
  stock         INT          NOT NULL DEFAULT 0      COMMENT '剩余库存，-1 表示不限',
  total_exchanged INT        NOT NULL DEFAULT 0      COMMENT '累计已兑换',
  need_address  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '实物是否需要收货地址',
  status        TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '0下架 1上架',
  sort_order    INT          NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status),
  KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '积分商城礼品表';

-- 种子数据（覆盖设计稿常见档位）
INSERT INTO mo_points_goods (id, name, description, image, category, points, stock, need_address, status, sort_order, create_time) VALUES
(1, 'MOYUYO 帆布袋',           '品牌联名帆布手提袋',         'https://picsum.photos/300/300?random=81', 'PHYSICAL', 500,  100, 1, 1, 10, NOW()),
(2, '¥5 通用优惠券',           '满 ¥30 可用，有效期 30 天',     'https://picsum.photos/300/300?random=82', 'COUPON',   1000, -1,   0, 1, 20, NOW()),
(3, '免邮券',                   '单笔订单免运费一次',           'https://picsum.photos/300/300?random=83', 'COUPON_FREESHIP', 800, -1, 0, 1, 30, NOW()),
(4, '¥20 通用优惠券',           '满 ¥99 可用，有效期 60 天',     'https://picsum.photos/300/300?random=84', 'COUPON',   3000, -1, 0, 1, 40, NOW()),
(5, '宠物磨牙玩具',             '天然橡胶磨牙棒',               'https://picsum.photos/300/300?random=85', 'PHYSICAL', 1500, 50,  1, 1, 50, NOW()),
(6, 'IP 限定贴纸套装',           'MOYUYO 4 张一套',               'https://picsum.photos/300/300?random=86', 'PHYSICAL', 300,  500, 1, 1, 60, NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 用户积分兑换记录表
CREATE TABLE IF NOT EXISTS mo_points_exchange (
  id            BIGINT       NOT NULL PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  goods_id      BIGINT       NOT NULL,
  goods_name    VARCHAR(128) NOT NULL,
  points_cost   INT          NOT NULL,
  receiver_name VARCHAR(64)  NULL,
  receiver_phone VARCHAR(20) NULL,
  receiver_address VARCHAR(512) NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SHIPPED/COMPLETED/CANCELLED',
  tracking_no   VARCHAR(64)  NULL,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_id (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '积分兑换记录';

-- 用户补签记录表（章节 2.1：每月可免费补签 1 次，之后 50 积分/次）
CREATE TABLE IF NOT EXISTS mo_checkin_makeup (
  id            BIGINT       NOT NULL PRIMARY KEY,
  user_id       BIGINT       NOT NULL,
  ymonth        VARCHAR(7)   NOT NULL                COMMENT 'yyyy-MM',
  count         INT          NOT NULL DEFAULT 0      COMMENT '当月已补签次数',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_ymonth (user_id, ymonth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '签到补签记录';
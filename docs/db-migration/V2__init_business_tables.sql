-- ============================================================
-- V2__init_business_tables.sql
-- 业务表：商品 / 订单 / 支付 / 营销 / 社区 / 同步等
-- ============================================================

-- 1. 商品分类
CREATE TABLE mo_category (
  id          BIGINT       NOT NULL,
  parent_id   BIGINT       DEFAULT 0,
  name        VARCHAR(64)  NOT NULL,
  icon        VARCHAR(255) NULL,
  sort        INT          DEFAULT 0,
  level       TINYINT      DEFAULT 1,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '商品分类表';

-- 2. 品牌
CREATE TABLE mo_brand (
  id          BIGINT       NOT NULL,
  name        VARCHAR(64)  NOT NULL,
  logo        VARCHAR(255) NULL,
  description TEXT         NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '品牌表';

-- 3. 商品 SPU
CREATE TABLE mo_product (
  id              BIGINT         NOT NULL,
  spu_code        VARCHAR(32)    NOT NULL                COMMENT 'SPU 编码',
  woo_product_id  BIGINT         NULL                    COMMENT 'WooCommerce 商品 ID',
  category_id     BIGINT         NOT NULL,
  brand_id        BIGINT         NULL,
  name            VARCHAR(255)   NOT NULL,
  main_image      VARCHAR(255)   NULL,
  price           DECIMAL(10,2)  NOT NULL DEFAULT 0,
  original_price  DECIMAL(10,2)  NULL,
  stock           INT            NOT NULL DEFAULT 0,
  stock_status    VARCHAR(16)    NOT NULL DEFAULT 'IN_STOCK',
  sales           INT            NOT NULL DEFAULT 0,
  attributes      JSON           NULL,
  detail          TEXT           NULL,
  on_sale         TINYINT(1)     NOT NULL DEFAULT 1,
  woo_modified    DATETIME       NULL,
  create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_spu_code (spu_code),
  KEY idx_category (category_id),
  KEY idx_woo_product (woo_product_id),
  KEY idx_on_sale (on_sale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '商品 SPU 表';

-- 4. 商品 SKU
CREATE TABLE mo_product_sku (
  id                  BIGINT         NOT NULL,
  product_id          BIGINT         NOT NULL,
  sku_code            VARCHAR(32)    NOT NULL,
  woo_variation_id    BIGINT         NULL,
  spec                VARCHAR(255)   NULL,
  price               DECIMAL(10,2)  NOT NULL DEFAULT 0,
  stock               INT            NOT NULL DEFAULT 0,
  sales               INT            NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_code (sku_code),
  KEY idx_product (product_id),
  KEY idx_woo_variation (woo_variation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '商品 SKU 表';

-- 5. 商品图片
CREATE TABLE mo_product_image (
  id          BIGINT       NOT NULL,
  product_id  BIGINT       NOT NULL,
  url         VARCHAR(255) NOT NULL,
  sort        INT          DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '商品图片表';

-- 6. 收货地址
CREATE TABLE mo_address (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  receiver    VARCHAR(64)  NOT NULL                COMMENT '收件人',
  phone       VARCHAR(20)  NULL,
  country     VARCHAR(8)   NOT NULL                COMMENT '国家代码',
  province    VARCHAR(64)  NULL                    COMMENT '州 / 省',
  city        VARCHAR(64)  NULL,
  district    VARCHAR(64)  NULL,
  detail      VARCHAR(255) NOT NULL                COMMENT '详细地址',
  zip_code    VARCHAR(16)  NULL,
  tag         VARCHAR(16)  NULL                    COMMENT 'HOME/COMPANY/OTHER',
  is_default  TINYINT(1)   DEFAULT 0,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '收货地址表';

-- 7. 购物车
CREATE TABLE mo_cart (
  id          BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  sku_id      BIGINT   NOT NULL,
  quantity    INT      NOT NULL DEFAULT 1,
  selected    TINYINT(1) DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_sku (user_id, sku_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '购物车表';

-- 8. 订单
CREATE TABLE mo_order (
  id                   BIGINT         NOT NULL,
  order_no             VARCHAR(32)    NOT NULL                COMMENT '业务订单号',
  user_id              BIGINT         NOT NULL,
  address_id           BIGINT         NULL,
  goods_amount         DECIMAL(10,2)  NOT NULL DEFAULT 0,
  freight              DECIMAL(10,2)  NOT NULL DEFAULT 0,
  tax_amount           DECIMAL(10,2)  NOT NULL DEFAULT 0      COMMENT '税费（欧美 VAT）',
  coupon_discount      DECIMAL(10,2)  NOT NULL DEFAULT 0,
  points_discount      DECIMAL(10,2)  NOT NULL DEFAULT 0,
  pay_amount           DECIMAL(10,2)  NOT NULL DEFAULT 0,
  currency             VARCHAR(8)     NOT NULL DEFAULT 'USD'  COMMENT '结算币种',
  coupon_id            VARCHAR(32)    NULL,
  points_used          INT            DEFAULT 0,
  status               VARCHAR(16)    NOT NULL                COMMENT 'PENDING_PAY/PENDING_SHIP/PENDING_RECEIVE/COMPLETED/CANCELLED/REFUNDING/REFUNDED',
  pay_channel          VARCHAR(16)    NULL                    COMMENT 'WECHAT/ALIPAY/PAYPAL/STRIPE/APPLE_PAY/GOOGLE_PAY',
  pay_transaction_id   VARCHAR(64)    NULL,
  woo_order_id         BIGINT         NULL                    COMMENT 'WooCommerce 订单 ID',
  sync_status          TINYINT(1)     DEFAULT 0               COMMENT '0 待同步 1 成功 2 失败',
  sync_retry_count     INT            DEFAULT 0,
  sync_last_time       DATETIME       NULL,
  tracking_number      VARCHAR(64)    NULL,
  shipping_carrier     VARCHAR(32)    NULL,
  remark               TEXT           NULL,
  create_time          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at              DATETIME       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_status (user_id, status),
  KEY idx_create_time (create_time),
  KEY idx_woo_order (woo_order_id),
  KEY idx_sync_status (sync_status, sync_retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '订单表';

-- 9. 订单明细
CREATE TABLE mo_order_item (
  id            BIGINT         NOT NULL,
  order_id      BIGINT         NOT NULL,
  product_id    BIGINT         NOT NULL,
  sku_id        BIGINT         NOT NULL,
  product_name  VARCHAR(255)   NOT NULL,
  sku_spec      VARCHAR(255)   NULL,
  main_image    VARCHAR(255)   NULL,
  price         DECIMAL(10,2)  NOT NULL,
  quantity      INT            NOT NULL,
  subtotal      DECIMAL(10,2)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '订单明细表';

-- 10. 支付记录
CREATE TABLE mo_payment (
  id              BIGINT         NOT NULL,
  order_id        BIGINT         NOT NULL,
  pay_channel     VARCHAR(16)    NOT NULL,
  transaction_id  VARCHAR(64)    NULL,
  amount          DECIMAL(10,2)  NOT NULL,
  currency        VARCHAR(8)     NOT NULL DEFAULT 'USD',
  status          VARCHAR(16)    NOT NULL,
  paid_at         DATETIME       NULL,
  raw_response    JSON           NULL,
  create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order_id (order_id),
  KEY idx_transaction (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '支付记录表';

-- 11. 退款
CREATE TABLE mo_refund (
  id            BIGINT         NOT NULL,
  order_id      BIGINT         NOT NULL,
  refund_no     VARCHAR(32)    NOT NULL,
  type          VARCHAR(16)    NOT NULL                COMMENT 'REFUND_ONLY/REFUND_RETURN',
  amount        DECIMAL(10,2)  NOT NULL,
  reason        VARCHAR(64)    NULL,
  description   TEXT           NULL,
  images        JSON           NULL,
  status        VARCHAR(16)    NOT NULL,
  woo_refund_id BIGINT         NULL,
  create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  complete_time DATETIME       NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '退款表';

-- 12. 物流
CREATE TABLE mo_logistics (
  id              BIGINT       NOT NULL,
  order_id        BIGINT       NOT NULL,
  carrier         VARCHAR(32)  NULL                    COMMENT '承运商：SF/YTO/AMAZON/USPS/FEDEX',
  tracking_number VARCHAR(64)  NULL,
  traces          JSON         NULL,
  shipped_at      DATETIME     NULL,
  received_at     DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_order_id (order_id),
  KEY idx_tracking (tracking_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '物流表';

-- 13. 同步日志
CREATE TABLE mo_sync_log (
  id           BIGINT       NOT NULL,
  biz_type     VARCHAR(32)  NOT NULL                COMMENT 'ORDER/PRODUCT/REFUND',
  biz_no       VARCHAR(32)  NOT NULL,
  woo_id       BIGINT       NULL,
  direction    VARCHAR(16)  NOT NULL                COMMENT 'PUSH/PULL/CALLBACK',
  status       VARCHAR(16)  NOT NULL                COMMENT 'SUCCESS/FAILED',
  request      TEXT         NULL                    COMMENT '请求体（脱敏）',
  response     TEXT         NULL,
  error_msg    TEXT         NULL,
  retry_count  INT          DEFAULT 0,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_biz_no (biz_no),
  KEY idx_status_create (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'WooCommerce 同步日志表';

-- 14. 同步位点
CREATE TABLE mo_sync_checkpoint (
  id              VARCHAR(32) NOT NULL,
  biz_type        VARCHAR(32) NOT NULL,
  last_sync_time  VARCHAR(32) NULL,
  last_sync_id    VARCHAR(32) NULL,
  update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '同步位点表';

-- 15. 优惠券
CREATE TABLE mo_coupon (
  id          BIGINT         NOT NULL,
  name        VARCHAR(64)    NOT NULL,
  type        VARCHAR(16)    NOT NULL                COMMENT 'AMOUNT/PERCENT',
  amount      DECIMAL(10,2)  NOT NULL,
  min_amount  DECIMAL(10,2)  NOT NULL DEFAULT 0,
  start_time  DATETIME       NOT NULL,
  end_time    DATETIME       NOT NULL,
  total       INT            NOT NULL DEFAULT 0,
  remain      INT            NOT NULL DEFAULT 0,
  status      TINYINT(1)     NOT NULL DEFAULT 1,
  create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status_time (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '优惠券表';

-- 16. 用户优惠券
CREATE TABLE mo_user_coupon (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  coupon_id   BIGINT       NOT NULL,
  status      VARCHAR(16)  NOT NULL                COMMENT 'UNUSED/USED/EXPIRED',
  receive_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  use_time    DATETIME     NULL,
  use_order_no VARCHAR(32) NULL,
  PRIMARY KEY (id),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '用户优惠券表';

-- 17. 积分流水
CREATE TABLE mo_points_log (
  id          BIGINT  NOT NULL,
  user_id     BIGINT  NOT NULL,
  change_value INT     NOT NULL,
  type        VARCHAR(32) NOT NULL             COMMENT 'SIGN_IN/ORDER/REFUND/EXCHANGE/EXPIRE',
  biz_no      VARCHAR(32) NULL,
  remark      TEXT     NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '积分流水表';

-- 18. 会员
CREATE TABLE mo_member (
  id              BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  level           VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
  growth_value    INT          NOT NULL DEFAULT 0,
  level_expire_at DATETIME     NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '会员表';

-- 19. 社区帖子
CREATE TABLE mo_community_post (
  id            BIGINT   NOT NULL,
  user_id       BIGINT   NOT NULL,
  content       TEXT     NULL,
  images        JSON     NULL,
  video         VARCHAR(255) NULL,
  topic         VARCHAR(64) NULL,
  product_ids   JSON     NULL,
  like_count    INT      DEFAULT 0,
  comment_count INT      DEFAULT 0,
  collect_count INT      DEFAULT 0,
  view_count    INT      DEFAULT 0,
  status        TINYINT(1) DEFAULT 1              COMMENT '0 删除 1 正常 2 审核中',
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '社区帖子表';

-- 20. 社区评论
CREATE TABLE mo_community_comment (
  id          BIGINT  NOT NULL,
  post_id     BIGINT  NOT NULL,
  user_id     BIGINT  NOT NULL,
  parent_id   BIGINT  DEFAULT 0,
  content     TEXT    NOT NULL,
  status      TINYINT(1) DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '社区评论表';

-- 21. 点赞
CREATE TABLE mo_community_like (
  id          BIGINT  NOT NULL,
  post_id     BIGINT  NOT NULL,
  user_id     BIGINT  NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '社区点赞表';

-- 22. 收藏
CREATE TABLE mo_community_collect (
  id          BIGINT  NOT NULL,
  post_id     BIGINT  NOT NULL,
  user_id     BIGINT  NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '社区收藏表';

-- 23. 消息中心
CREATE TABLE mo_message (
  id          BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  type        VARCHAR(16) NOT NULL              COMMENT 'SYSTEM/ORDER/PROMO/COMMUNITY',
  title       VARCHAR(128) NULL,
  content     TEXT     NULL,
  payload     JSON     NULL,
  read_flag   TINYINT(1) DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, read_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '消息中心表';

-- 24. 宠物档案
CREATE TABLE mo_pet (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  name        VARCHAR(32)  NOT NULL,
  type        VARCHAR(16)  NOT NULL                COMMENT 'DOG/CAT/OTHER',
  breed       VARCHAR(64)  NULL,
  gender      TINYINT(1)   NULL                    COMMENT '1 公 2 母',
  birthday    DATE         NULL,
  weight_kg   DECIMAL(5,2) NULL,
  avatar      VARCHAR(255) NULL,
  tags        JSON         NULL,
  status      TINYINT(1)   DEFAULT 1,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '宠物档案表';

-- 25. 优惠券适用商品
CREATE TABLE mo_coupon_product (
  id          BIGINT NOT NULL,
  coupon_id   BIGINT NOT NULL,
  product_id  BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupon_product (coupon_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '优惠券适用商品表';

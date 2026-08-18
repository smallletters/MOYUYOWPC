-- ============================================================
-- V8__init_product_review.sql
-- 商品评价系统：评价表 + 索引
-- ============================================================

CREATE TABLE mo_product_review (
  id              BIGINT        NOT NULL                  COMMENT '雪花 ID',
  product_id      BIGINT        NOT NULL                  COMMENT '商品 ID',
  user_id         BIGINT        NOT NULL                  COMMENT '用户 ID',
  order_id        BIGINT        NULL                      COMMENT '订单 ID',
  order_item_id   BIGINT        NULL                      COMMENT '订单明细 ID',
  rating          TINYINT       NOT NULL DEFAULT 5         COMMENT '评分 1-5',
  content         TEXT          NULL                      COMMENT '评价内容',
  tags            JSON          NULL                      COMMENT '评价标签 ["Good quality","Fast delivery"]',
  images          JSON          NULL                      COMMENT '评价图片 URL 数组',
  status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_product (product_id),
  KEY idx_user (user_id),
  KEY idx_order (order_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '商品评价表';

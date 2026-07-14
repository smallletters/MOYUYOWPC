-- ============================================================
-- V7__init_extra_tables.sql  - MOYUYO 补充业务表
-- 收藏夹 / 会员体系 / 营销活动 / 售后 / 健康日历 / 社区话题
-- ============================================================

-- 1. 收藏分组
CREATE TABLE mo_favorites_group (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  name        VARCHAR(64)  NOT NULL COMMENT '分组名称',
  sort_order  INT          NOT NULL DEFAULT 0,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '收藏分组表';

-- 2. 收藏夹
CREATE TABLE mo_favorites (
  id              BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  product_id      BIGINT       NOT NULL,
  sku_id          BIGINT       NULL,
  group_id        BIGINT       NULL COMMENT '分组ID，NULL为默认分组',
  price_alert_flag TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '0不提醒 1降价提醒',
  alert_price     DECIMAL(10,2) NULL COMMENT '提醒阈值',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_product_sku (user_id, product_id, sku_id),
  KEY idx_user_group (user_id, group_id),
  KEY idx_user_price_alert (user_id, price_alert_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '收藏夹表';

-- 3. 储值卡
CREATE TABLE mo_member_wallet (
  id              BIGINT        NOT NULL,
  user_id         BIGINT        NOT NULL,
  balance         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
  total_recharged DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计充值',
  total_spent     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费',
  status          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '0正常 1冻结',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '储值卡表';

-- 4. Prime 订阅
CREATE TABLE mo_member_prime (
  id                  BIGINT       NOT NULL,
  user_id             BIGINT       NOT NULL,
  plan                VARCHAR(16)  NOT NULL COMMENT 'MONTHLY/ANNUAL',
  pay_channel         VARCHAR(32)  NOT NULL COMMENT 'STRIPE/PAYPAL',
  pay_subscription_id VARCHAR(128) NULL COMMENT '支付平台订阅ID',
  status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELLED/EXPIRED',
  expire_at           DATETIME     NULL COMMENT '到期时间',
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id),
  KEY idx_status_expire (status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'Prime订阅表';

-- 5. 任务中心
CREATE TABLE mo_member_task (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  task_code   VARCHAR(64)  NOT NULL COMMENT '任务编码',
  task_type   VARCHAR(16)  NOT NULL COMMENT 'DAILY/WEEKLY/ACHIEVEMENT',
  progress    INT          NOT NULL DEFAULT 0,
  total       INT          NOT NULL DEFAULT 1,
  rewards_json JSON       NULL COMMENT '奖励配置',
  status      VARCHAR(16)  NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/COMPLETED/CLAIMED',
  claim_time  DATETIME     NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_task (user_id, task_code),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '任务中心表';

-- 6. 营销活动
CREATE TABLE mo_marketing_activity (
  id          BIGINT       NOT NULL,
  type        VARCHAR(16)  NOT NULL COMMENT 'SALE/GROUPON/SECKILL/LOTTERY',
  name        VARCHAR(128) NOT NULL,
  rules_json  JSON         NULL COMMENT '活动规则',
  start_time  DATETIME     NOT NULL,
  end_time    DATETIME     NOT NULL,
  status      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0关闭 1开启',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_type (type),
  KEY idx_time_range (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '营销活动表';

-- 7. 邀请记录
CREATE TABLE mo_invite_record (
  id              BIGINT       NOT NULL,
  inviter_user_id BIGINT       NOT NULL,
  invite_code     VARCHAR(32)  NOT NULL,
  invitee_user_id BIGINT       NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REGISTERED/REWARDED',
  rewards_json    JSON         NULL COMMENT '奖励配置',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_inviter (inviter_user_id, status),
  KEY idx_invite_code (invite_code),
  KEY idx_invitee (invitee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '邀请记录表';

-- 8. 退货申请
CREATE TABLE mo_return_request (
  id            BIGINT        NOT NULL,
  order_no      VARCHAR(64)   NOT NULL,
  user_id       BIGINT        NOT NULL,
  items_json    JSON          NOT NULL COMMENT '退货商品列表',
  reason        VARCHAR(32)   NOT NULL COMMENT 'SIZE_WRONG/QUALITY_ISSUE/DAMAGE/CHANGE_MIND/OTHER',
  description   VARCHAR(512)  NULL,
  images        JSON          NULL,
  refund_amount DECIMAL(10,2) NOT NULL,
  refund_method VARCHAR(32)   NOT NULL DEFAULT 'ORIGINAL' COMMENT 'ORIGINAL/WALLET/POINTS',
  status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/SHIPPED/COMPLETED',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order_no (order_no),
  KEY idx_user_status (user_id, status),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '退货申请表';

-- 9. 意见反馈
CREATE TABLE mo_feedback (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  type        VARCHAR(32)  NOT NULL COMMENT 'SUGGESTION/COMPLAINT/BUG/OTHER',
  content     TEXT         NOT NULL,
  images      JSON         NULL,
  contact     VARCHAR(128) NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_type (user_id, type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '意见反馈表';

-- 10. 健康日历
CREATE TABLE mo_pet_health_calendar (
  id                BIGINT       NOT NULL,
  pet_id            BIGINT       NOT NULL,
  event_type        VARCHAR(32)  NOT NULL COMMENT 'BATH/VACCINE/DEWORM/EXAM',
  event_name        VARCHAR(128) NOT NULL,
  event_date        DATE         NOT NULL,
  status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/OVERDUE',
  notes             VARCHAR(512) NULL,
  next_remind_date  DATE         NULL,
  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pet_date (pet_id, event_date),
  KEY idx_pet_type (pet_id, event_type),
  KEY idx_next_remind (next_remind_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '健康日历表';

-- 11. 社区话题
CREATE TABLE mo_community_topic (
  id           BIGINT       NOT NULL,
  name         VARCHAR(64)  NOT NULL,
  description  VARCHAR(512) NULL,
  cover_image  VARCHAR(256) NULL,
  post_count   INT          NOT NULL DEFAULT 0,
  follow_count INT          NOT NULL DEFAULT 0,
  status       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post_count (post_count DESC),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '社区话题表';

-- 12. 社区关注关系
CREATE TABLE mo_community_follow (
  id             BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  follow_user_id BIGINT       NOT NULL,
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_follow (user_id, follow_user_id),
  KEY idx_follow_user (follow_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '社区关注关系表';

-- ============================================================
-- 补充 V1 mo_device 升级：新增设备登录时间等字段（如需要后续处理）
-- 补充 V2 mo_sync_checkpoint.biz_type 唯一性索引
-- ============================================================

-- 13. 同步位点补充唯一索引
CREATE UNIQUE INDEX uk_biz_type ON mo_sync_checkpoint(biz_type);

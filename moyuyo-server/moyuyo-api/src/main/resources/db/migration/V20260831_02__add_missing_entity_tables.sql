-- ============================================================
-- V20260831_02__add_missing_entity_tables.sql
-- 补齐 Entity 已声明但 migration 缺失的 14 张表
-- 来源: diff-tables.ps1 比对 Entity @TableName vs migration CREATE TABLE
-- ============================================================

-- 1) mo_achievement 成就定义
CREATE TABLE IF NOT EXISTS `mo_achievement` (
  `id`              BIGINT        NOT NULL                COMMENT '雪花ID',
  `code`            VARCHAR(64)   NOT NULL                COMMENT '成就编码',
  `name`            VARCHAR(128)  NOT NULL                COMMENT '成就名称',
  `description`     VARCHAR(512)  NULL                    COMMENT '描述',
  `icon`            VARCHAR(256)  NULL                    COMMENT '图标',
  `badge_image`     VARCHAR(256)  NULL                    COMMENT '徽章图',
  `points_reward`   INT           NOT NULL DEFAULT 0      COMMENT '积分奖励',
  `condition_expr`  VARCHAR(512)  NULL                    COMMENT '解锁条件表达式',
  `category`        VARCHAR(16)   NULL                    COMMENT 'COMMON/RARE/EPIC/LEGEND',
  `active`          TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '0下线 1启用',
  `sort_order`      INT           NOT NULL DEFAULT 0      COMMENT '排序',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成就定义表';

-- 2) mo_user_achievement 用户成就进度
CREATE TABLE IF NOT EXISTS `mo_user_achievement` (
  `id`             BIGINT    NOT NULL                COMMENT '雪花ID',
  `user_id`        BIGINT    NOT NULL                COMMENT '用户ID',
  `achievement_id` BIGINT    NOT NULL                COMMENT '成就ID',
  `unlocked_at`    DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_achievement` (`achievement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成就进度';

-- 3) mo_affiliate_account 分销账号
CREATE TABLE IF NOT EXISTS `mo_affiliate_account` (
  `id`                BIGINT         NOT NULL                COMMENT '雪花ID',
  `user_id`           BIGINT         NOT NULL                COMMENT '用户ID',
  `level`             VARCHAR(16)    NOT NULL DEFAULT 'BRONZE' COMMENT 'BRONZE/SILVER/GOLD/PLATINUM',
  `total_invites`     INT            NOT NULL DEFAULT 0      COMMENT '累计邀请数',
  `total_orders`      INT            NOT NULL DEFAULT 0      COMMENT '累计成单数',
  `total_commission`  DECIMAL(12,2)  NOT NULL DEFAULT 0.00   COMMENT '累计佣金',
  `withdrawn_amount`  DECIMAL(12,2)  NOT NULL DEFAULT 0.00   COMMENT '已提现金额',
  `available_amount`  DECIMAL(12,2)  NOT NULL DEFAULT 0.00   COMMENT '可提现金额',
  `status`            VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/FROZEN/CLOSED',
  `create_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销账号';

-- 4) mo_affiliate_commission 分销佣金明细
CREATE TABLE IF NOT EXISTS `mo_affiliate_commission` (
  `id`            BIGINT         NOT NULL                COMMENT '雪花ID',
  `user_id`       BIGINT         NOT NULL                COMMENT '分销用户ID',
  `order_id`      BIGINT         NOT NULL                COMMENT '关联订单ID',
  `order_amount`  DECIMAL(12,2)  NOT NULL DEFAULT 0.00   COMMENT '订单金额',
  `rate`          DECIMAL(5,4)   NOT NULL DEFAULT 0.0000 COMMENT '佣金比例',
  `amount`        DECIMAL(12,2)  NOT NULL DEFAULT 0.00   COMMENT '佣金金额',
  `status`        VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED/WITHDRAWN',
  `settle_time`   DATETIME       NULL                    COMMENT '结算时间',
  `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分销佣金明细';

-- 5) mo_block 用户拉黑
CREATE TABLE IF NOT EXISTS `mo_block` (
  `id`          BIGINT       NOT NULL                COMMENT '雪花ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '发起拉黑的用户ID',
  `target_id`   BIGINT       NOT NULL                COMMENT '被拉黑的目标ID',
  `reason`      VARCHAR(256) NULL                    COMMENT '原因',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_id`),
  KEY `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户拉黑关系';

-- 6) mo_coupon_transfer_log 优惠券转让日志
CREATE TABLE IF NOT EXISTS `mo_coupon_transfer_log` (
  `id`            BIGINT    NOT NULL                COMMENT '雪花ID',
  `user_coupon_id` BIGINT   NOT NULL                COMMENT '用户券ID',
  `from_user_id`  BIGINT    NOT NULL                COMMENT '转出人',
  `to_user_id`    BIGINT    NOT NULL                COMMENT '转入人',
  `transfer_time` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_coupon` (`user_coupon_id`),
  KEY `idx_from` (`from_user_id`),
  KEY `idx_to` (`to_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券转让日志';

-- 7) mo_festival_event 节日活动
CREATE TABLE IF NOT EXISTS `mo_festival_event` (
  `id`           BIGINT        NOT NULL                COMMENT '雪花ID',
  `name`         VARCHAR(128)  NOT NULL                COMMENT '活动名',
  `subtitle`     VARCHAR(256)  NULL                    COMMENT '副标题',
  `cover_image`  VARCHAR(256)  NULL                    COMMENT '封面图',
  `banner_json`  JSON          NULL                    COMMENT '轮播图列表',
  `start_time`   DATETIME      NOT NULL                COMMENT '开始时间',
  `end_time`     DATETIME      NOT NULL                COMMENT '结束时间',
  `reward_json`  JSON          NULL                    COMMENT '奖励配置',
  `active`       TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '0下线 1启用',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_active_time` (`active`, `start_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节日活动';

-- 8) mo_follow 关注关系
CREATE TABLE IF NOT EXISTS `mo_follow` (
  `id`          BIGINT       NOT NULL                COMMENT '雪花ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '关注者',
  `target_id`   BIGINT       NOT NULL                COMMENT '被关注者',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'FOLLOWING' COMMENT 'FOLLOWING/MUTED/BLOCKED',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_id`),
  KEY `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系';

-- 9) mo_help_category 帮助中心分类
CREATE TABLE IF NOT EXISTS `mo_help_category` (
  `id`          BIGINT       NOT NULL                COMMENT '雪花ID',
  `name`        VARCHAR(128) NOT NULL                COMMENT '分类名',
  `icon`        VARCHAR(256) NULL                    COMMENT '分类图标',
  `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序',
  `active`      TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '0下线 1启用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帮助中心分类';

-- 10) mo_help_article 帮助文章
CREATE TABLE IF NOT EXISTS `mo_help_article` (
  `id`             BIGINT        NOT NULL                COMMENT '雪花ID',
  `category_id`    BIGINT        NOT NULL                COMMENT '分类ID',
  `title`          VARCHAR(256)  NOT NULL                COMMENT '标题',
  `content`        MEDIUMTEXT    NULL                    COMMENT '正文',
  `tags`           VARCHAR(512)  NULL                    COMMENT '标签(逗号分隔)',
  `view_count`     INT           NOT NULL DEFAULT 0      COMMENT '浏览量',
  `helpful_count`  INT           NOT NULL DEFAULT 0      COMMENT '有用计数',
  `status`         TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '0下线 1发布',
  `sort_order`     INT           NOT NULL DEFAULT 0      COMMENT '排序',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帮助文章';

-- 11) mo_newuser_gift 新人礼包定义
CREATE TABLE IF NOT EXISTS `mo_newuser_gift` (
  `id`                  BIGINT         NOT NULL                COMMENT '雪花ID',
  `name`                VARCHAR(128)   NOT NULL                COMMENT '礼包名',
  `amount`              DECIMAL(10,2)  NOT NULL DEFAULT 0.00   COMMENT '现金金额',
  `coupon_id`           BIGINT         NULL                    COMMENT '关联券模板ID',
  `points`              INT            NOT NULL DEFAULT 0      COMMENT '赠送积分',
  `claim_window_days`   INT            NOT NULL DEFAULT 7      COMMENT '领取有效期(天)',
  `active`              TINYINT(1)     NOT NULL DEFAULT 1      COMMENT '0下线 1启用',
  `create_time`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新人礼包定义';

-- 12) mo_newuser_gift_claim 新人礼包领取记录
CREATE TABLE IF NOT EXISTS `mo_newuser_gift_claim` (
  `id`          BIGINT       NOT NULL                COMMENT '雪花ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '领取用户ID',
  `gift_id`     BIGINT       NOT NULL                COMMENT '礼包ID',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'CLAIMED' COMMENT 'CLAIMED/USED/EXPIRED',
  `expire_at`   DATETIME     NULL                    COMMENT '过期时间',
  `claim_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_gift` (`user_id`, `gift_id`),
  KEY `idx_gift` (`gift_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新人礼包领取记录';

-- 13) mo_service_booking 服务预约
CREATE TABLE IF NOT EXISTS `mo_service_booking` (
  `id`             BIGINT         NOT NULL                COMMENT '雪花ID',
  `user_id`        BIGINT         NOT NULL                COMMENT '用户ID',
  `pet_id`         BIGINT         NOT NULL                COMMENT '宠物ID',
  `service_type`   VARCHAR(16)    NOT NULL                COMMENT 'BATH/GROOMING/VET/HOTEL/TRAIN',
  `staff_id`       BIGINT         NULL                    COMMENT '服务人员ID',
  `booking_date`   DATE           NOT NULL                COMMENT '预约日期',
  `booking_time`   TIME           NOT NULL                COMMENT '预约时间',
  `duration_min`   INT            NOT NULL DEFAULT 60     COMMENT '服务时长(分钟)',
  `address`        VARCHAR(256)   NULL                    COMMENT '地址',
  `contact_phone`  VARCHAR(32)    NULL                    COMMENT '联系电话',
  `notes`          VARCHAR(512)   NULL                    COMMENT '备注',
  `price`          DECIMAL(10,2)  NOT NULL DEFAULT 0.00   COMMENT '价格',
  `status`         VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/COMPLETED/CANCELLED',
  `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_pet` (`pet_id`),
  KEY `idx_date` (`booking_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务预约';

-- 14) mo_user_device 用户设备
CREATE TABLE IF NOT EXISTS `mo_user_device` (
  `id`            BIGINT       NOT NULL                COMMENT '雪花ID',
  `user_id`       BIGINT       NOT NULL                COMMENT '用户ID',
  `device_id`     VARCHAR(128) NOT NULL                COMMENT '设备唯一标识',
  `device_name`   VARCHAR(128) NULL                    COMMENT '设备名',
  `device_type`   VARCHAR(16)  NOT NULL                COMMENT 'IOS/ANDROID/WEB/MAC/WINDOWS',
  `os_version`    VARCHAR(64)  NULL                    COMMENT '系统版本',
  `app_version`   VARCHAR(32)  NULL                    COMMENT 'APP版本',
  `ip_address`    VARCHAR(64)  NULL                    COMMENT 'IP',
  `location`      VARCHAR(128) NULL                    COMMENT '登录地',
  `trusted`       TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '0否 1是信任设备',
  `last_active`   DATETIME     NULL                    COMMENT '最近活跃时间',
  `login_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device` (`user_id`, `device_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_device_type` (`device_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设备';
